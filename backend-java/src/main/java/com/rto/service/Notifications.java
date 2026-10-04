package com.rto.service;

import com.rto.core.ApiException;
import com.rto.core.Clock;
import com.rto.core.CurrentUser;
import com.rto.core.Db;
import com.rto.core.PageParams;
import com.rto.core.PageResponse;
import com.rto.core.QB;
import com.rto.domain.Notification;
import com.rto.domain.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Notifications are queued and delivered AFTER the business transaction has committed, in their own transactions.
 * A failing provider marks the notification FAILED; it can never roll back or break the business operation.
 */
@Service
public class Notifications {
    private static final Logger log = LoggerFactory.getLogger(Notifications.class);
    private final Db db;
    private final NotificationProvider provider;
    private final TransactionTemplate tx;

    public Notifications(Db db, NotificationProvider provider, PlatformTransactionManager tm) {
        this.db = db;
        this.provider = provider;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Queue + deliver one SMS (always) and one EMAIL (when the person has an address) once the current tx commits. */
    public void notify(Long personId, String subject, String message) {
        if (personId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(personId, subject, message);
                }
            });
        } else {
            deliver(personId, subject, message);
        }
    }

    private void deliver(Long personId, String subject, String message) {
        try {
            List<Long> ids = new ArrayList<>();
            List<String> targets = new ArrayList<>();
            tx.executeWithoutResult(s -> {   // QUEUED rows are durable before any delivery attempt
                Person p = db.find(Person.class, personId);
                if (p == null) return;
                List<String[]> chans = new ArrayList<>();
                chans.add(new String[]{"SMS", p.getPhonePrimary()});
                if (p.getEmail() != null && !p.getEmail().isBlank()) chans.add(new String[]{"EMAIL", p.getEmail()});
                for (String[] c : chans) {
                    Notification n = new Notification();
                    n.setPersonId(personId);
                    n.setChannel(c[0]);
                    n.setSubject(subject.length() > 150 ? subject.substring(0, 150) : subject);
                    n.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
                    n.setStatus("QUEUED");
                    db.save(n);
                    ids.add(n.getNotificationId());
                    targets.add(c[1]);
                }
            });
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                String to = targets.get(i);
                tx.executeWithoutResult(s -> {
                    Notification n = db.get(Notification.class, id, "Notification");
                    try {
                        provider.send(n.getChannel(), to, n.getSubject(), n.getMessage());
                        n.setStatus("SENT");
                        n.setSentAt(Clock.now());
                    } catch (Exception e) {   // provider failure is recorded, never propagated
                        log.error("notification {} failed", id, e);
                        n.setStatus("FAILED");
                    }
                });
            }
        } catch (Exception e) {
            log.error("could not queue notification for person {}", personId, e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<Notification> list(CurrentUser user, PageParams p, String status, Long personId) {
        QB q = new QB("n", "Notification n");
        if (user.has("notification.view")) {
            q.eq("n.personId", personId);
        } else if (user.has("notification.view_own")) {
            q.and("n.personId = :me", "me", user.personId());
        } else {
            throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: notification.view");
        }
        q.eq("n.status", status);
        return db.page(q, Notification.class, p, Map.of("notification_id", "n.notificationId", "sent_at", "n.sentAt"),
                "n.notificationId", false);
    }
}
