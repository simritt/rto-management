package com.rto.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local-development provider: records deliveries in memory and logs them. Mark a real provider bean @Primary. */
@Component
public class MockNotificationProvider implements NotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(MockNotificationProvider.class);
    public final List<Map<String, String>> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String channel, String recipient, String subject, String message) {
        sent.add(Map.of("channel", channel, "to", recipient, "subject", String.valueOf(subject), "message", message));
        log.info("[mock {}] to={} subject={}", channel, recipient, subject);
    }
}
