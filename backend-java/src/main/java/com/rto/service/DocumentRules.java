package com.rto.service;

import com.rto.core.Clock;
import com.rto.domain.Document;
import com.rto.domain.DocumentType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure document-validity rules shared by the application workflow and the document API. */
public final class DocumentRules {
    private DocumentRules() {}

    /** A verified document is valid for document_types.validity_period_days from its verification. */
    public static LocalDateTime expiresAt(Document d, DocumentType t) {
        Integer days = t.getValidityPeriodDays();
        if ("VERIFIED".equals(d.getVerificationStatus()) && days != null && days > 0 && d.getVerifiedAt() != null) {
            return d.getVerifiedAt().plusDays(days);
        }
        return null;
    }

    /** Rejected and expired documents can never count as verified. */
    public static String effectiveStatus(Document d, DocumentType t) {
        if ("VERIFIED".equals(d.getVerificationStatus())) {
            LocalDateTime exp = expiresAt(d, t);
            if (exp != null && Clock.now().isAfter(exp)) return "EXPIRED";
        }
        return d.getVerificationStatus();
    }

    /** Later uploads supersede earlier ones of the same type (e.g. a re-upload after a rejection). */
    public static Map<Long, Document> latestPerType(List<Document> docs) {
        Map<Long, Document> latest = new HashMap<>();
        docs.stream().sorted(Comparator.comparing(Document::getUploadedAt).thenComparing(Document::getDocumentId))
                .forEach(d -> latest.put(d.getDocumentTypeId(), d));
        return latest;
    }
}
