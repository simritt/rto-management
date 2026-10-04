package com.rto.service;

import com.rto.core.*;
import com.rto.domain.*;
import com.rto.dto.ApplicationDto.DocumentOut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.rto.core.Audit.m;

@Service
@Transactional
public class DocumentService {
    private static final Map<String, byte[][]> ALLOWED = Map.of(
            ".pdf", new byte[][]{"%PDF".getBytes()},
            ".png", new byte[][]{{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}},
            ".jpg", new byte[][]{{(byte) 0xff, (byte) 0xd8, (byte) 0xff}},
            ".jpeg", new byte[][]{{(byte) 0xff, (byte) 0xd8, (byte) 0xff}});

    private final Db db;
    private final Audit audit;
    private final ApplicationService apps;
    private final Storage storage;
    private final Settings settings;

    public DocumentService(Db db, Audit audit, ApplicationService apps, Storage storage, Settings settings) {
        this.db = db;
        this.audit = audit;
        this.apps = apps;
        this.storage = storage;
        this.settings = settings;
    }

    public DocumentOut out(Document d) {
        DocumentType t = db.get(DocumentType.class, d.getDocumentTypeId(), "Document type");
        return new DocumentOut(d.getDocumentId(), d.getApplicationId(), d.getDocumentTypeId(), t.getTypeName(), d.getFilePath(),
                d.getVerificationStatus(), DocumentRules.effectiveStatus(d, t), d.getVerifiedByEmployeeId(), d.getRejectionReason(),
                d.getUploadedAt(), d.getVerifiedAt(), DocumentRules.expiresAt(d, t));
    }

    private static String extension(String filename) {
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int i = n.lastIndexOf('.');
        return i >= 0 ? n.substring(i) : "";
    }

    private static boolean startsWith(byte[] content, byte[] sig) {
        if (content.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if (content[i] != sig[i]) return false;
        return true;
    }

    public DocumentOut upload(Long applicationId, Long documentTypeId, MultipartFile file, CurrentUser user) {
        Application app = apps.get(applicationId, true);
        if (!(user.has("application.view") || apps.isOwner(user, app))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this application");
        }
        if (ApplicationService.TERMINAL.contains(app.getCurrentStatus())) {
            throw ApiException.conflict("APPLICATION_CLOSED", "Documents cannot be added to a " + app.getCurrentStatus() + " application");
        }
        db.get(DocumentType.class, documentTypeId, "Document type");

        String ext = extension(file.getOriginalFilename());
        byte[][] sigs = ALLOWED.get(ext);
        if (sigs == null) throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Only PDF, PNG and JPEG files are accepted");
        long limit = settings.maxUploadMb() * 1024L * 1024L;
        if (file.getSize() > limit) throw ApiException.badRequest("FILE_TOO_LARGE", "File exceeds the " + settings.maxUploadMb() + " MB limit");
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "The uploaded file could not be read");
        }
        if (content.length == 0) throw ApiException.badRequest("EMPTY_FILE", "Uploaded file is empty");
        boolean magicOk = false;
        for (byte[] sig : sigs) magicOk |= startsWith(content, sig);
        if (!magicOk) throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "File content does not match its extension");

        String path = storage.save("applications/" + applicationId, ext, content);   // bytes never go to MySQL
        Document d = new Document();
        d.setApplicationId(applicationId);
        d.setDocumentTypeId(documentTypeId);
        d.setFilePath(path);
        d.setVerificationStatus("PENDING");
        d.setUploadedAt(Clock.now());
        db.save(d);
        audit.record("documents", d.getDocumentId(), "INSERT", null,
                m("application_id", applicationId, "document_type_id", documentTypeId, "verification_status", "PENDING"), user.userId());
        db.flush();
        return out(d);
    }

    @Transactional(readOnly = true)
    public List<DocumentOut> list(Long applicationId, CurrentUser user) {
        Application app = apps.get(applicationId, false);
        if (!(user.has("document.view") || apps.isOwner(user, app))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this application's documents");
        }
        return db.list(Document.class, "select d from Document d where d.applicationId = :a order by d.documentId", "a", applicationId)
                .stream().map(this::out).toList();
    }

    public record FileContent(byte[] bytes, String path) {}

    @Transactional(readOnly = true)
    public FileContent file(Long documentId, CurrentUser user) {
        Document d = db.get(Document.class, documentId, "Document");
        Application app = apps.get(d.getApplicationId(), false);
        if (!(user.has("document.view") || apps.isOwner(user, app))) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have access to this document");
        }
        if (!storage.exists(d.getFilePath())) throw ApiException.conflict("FILE_MISSING", "The stored file is missing");
        return new FileContent(storage.read(d.getFilePath()), d.getFilePath());
    }

    private static Long reviewer(CurrentUser user) {
        if (user.employeeId() == null) throw ApiException.forbidden("NOT_AN_EMPLOYEE", "Only RTO employees can verify or reject documents");
        return user.employeeId();
    }

    public DocumentOut verify(Long id, CurrentUser user) {
        Long reviewer = reviewer(user);
        Document d = db.lock(Document.class, id, "Document");
        if (!"PENDING".equals(d.getVerificationStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only PENDING documents can be verified (this one is " + d.getVerificationStatus() + ")");
        }
        String old = d.getVerificationStatus();
        d.setVerificationStatus("VERIFIED");
        d.setVerifiedByEmployeeId(reviewer);
        d.setVerifiedAt(Clock.now());
        d.setRejectionReason(null);
        audit.record("documents", id, "UPDATE", m("verification_status", old),
                m("verification_status", "VERIFIED", "verified_by_employee_id", reviewer), user.userId());
        db.flush();
        return out(d);
    }

    public DocumentOut reject(Long id, String reasonIn, CurrentUser user) {
        Long reviewer = reviewer(user);
        String reason = reasonIn.strip();
        if (reason.length() < 5) throw ApiException.badRequest("REASON_REQUIRED", "A meaningful rejection reason is required");
        Document d = db.lock(Document.class, id, "Document");
        if (!"PENDING".equals(d.getVerificationStatus()) && !"VERIFIED".equals(d.getVerificationStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "A " + d.getVerificationStatus() + " document cannot be rejected");
        }
        String old = d.getVerificationStatus();
        d.setVerificationStatus("REJECTED");
        d.setVerifiedByEmployeeId(reviewer);
        d.setVerifiedAt(Clock.now());
        d.setRejectionReason(reason);
        audit.record("documents", id, "UPDATE", m("verification_status", old),
                m("verification_status", "REJECTED", "reason", reason, "verified_by_employee_id", reviewer), user.userId());
        db.flush();
        return out(d);
    }

    /** Persist EXPIRED for a verified document (after its validity lapsed, or on staff decision). */
    public DocumentOut expire(Long id, CurrentUser user) {
        reviewer(user);
        Document d = db.lock(Document.class, id, "Document");
        if (!"VERIFIED".equals(d.getVerificationStatus())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only VERIFIED documents can be expired");
        }
        d.setVerificationStatus("EXPIRED");
        audit.record("documents", id, "UPDATE", m("verification_status", "VERIFIED"), m("verification_status", "EXPIRED"), user.userId());
        db.flush();
        return out(d);
    }
}
