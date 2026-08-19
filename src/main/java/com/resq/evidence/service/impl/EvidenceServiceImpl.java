package com.resq.evidence.service.impl;

import com.resq.evidence.document.EvidenceMetadata;
import com.resq.evidence.document.SystemAuditEvent;
import com.resq.evidence.repository.EvidenceRepository;
import com.resq.evidence.service.AuditService;
import com.resq.evidence.service.EvidenceService;
import com.resq.evidence.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class EvidenceServiceImpl implements EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceServiceImpl.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "application/pdf",
            "application/octet-stream"
    );

    private final EvidenceRepository evidenceRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    @Autowired
    public EvidenceServiceImpl(
            EvidenceRepository evidenceRepository,
            StorageService storageService,
            AuditService auditService) {
        this.evidenceRepository = evidenceRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Override
    public EvidenceMetadata uploadEvidence(String incidentId, MultipartFile file, String description, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Evidence upload file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of 10MB (file size: " + (file.getSize() / 1024 / 1024) + "MB)");
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType + ". Allowed: JPEG, PNG, WEBP, PDF");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file stream: " + e.getMessage(), e);
        }

        // 1. Upload binary stream to Google Cloud Storage
        StorageService.UploadResult uploadResult = storageService.upload(
                incidentId,
                file.getOriginalFilename(),
                contentType != null ? contentType : "application/octet-stream",
                fileBytes
        );

        // 2. Save metadata in MongoDB
        EvidenceMetadata metadata = new EvidenceMetadata(
                incidentId,
                uploadResult.getFileName(),
                file.getOriginalFilename(),
                contentType,
                file.getSize(),
                uploadResult.getBucket(),
                uploadResult.getObjectName(),
                uploadResult.getFileUrl(),
                description,
                uploadedBy
        );

        EvidenceMetadata saved = evidenceRepository.save(metadata);
        log.info("Saved evidence metadata to MongoDB [id={}, fileName={}, incidentId={}]", saved.getId(), saved.getFileName(), incidentId);

        // 3. Publish operational audit event to Firestore
        Map<String, Object> eventMeta = new HashMap<>();
        eventMeta.put("evidenceId", saved.getId());
        eventMeta.put("fileName", saved.getFileName());
        eventMeta.put("sizeBytes", saved.getSize());
        eventMeta.put("uploadedBy", uploadedBy);
        eventMeta.put("bucket", uploadResult.getBucket());

        auditService.recordEvent("EVIDENCE_UPLOADED", incidentId, "evidence-service", "INFO", eventMeta);

        return saved;
    }

    @Override
    public List<EvidenceMetadata> getEvidenceByIncident(String incidentId) {
        return evidenceRepository.findByIncidentIdOrderByUploadedAtDesc(incidentId);
    }

    @Override
    public EvidenceMetadata getEvidenceById(String id) {
        return evidenceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Evidence metadata not found with ID: " + id));
    }

    @Override
    public EvidenceDownload downloadEvidence(String id) {
        EvidenceMetadata metadata = getEvidenceById(id);
        byte[] data = storageService.download(metadata.getBucket(), metadata.getObjectName());
        return new EvidenceDownload(data, metadata.getOriginalFileName(), metadata.getContentType());
    }

    @Override
    public void deleteEvidence(String id) {
        EvidenceMetadata metadata = getEvidenceById(id);
        storageService.delete(metadata.getBucket(), metadata.getObjectName());
        evidenceRepository.delete(metadata);
        log.info("Deleted evidence [id={}, object={}]", id, metadata.getObjectName());

        Map<String, Object> eventMeta = new HashMap<>();
        eventMeta.put("evidenceId", id);
        eventMeta.put("fileName", metadata.getFileName());
        auditService.recordEvent("EVIDENCE_DELETED", metadata.getIncidentId(), "evidence-service", "WARN", eventMeta);
    }

    @Override
    public List<SystemAuditEvent> getAuditEvents() {
        return auditService.getRecentEvents();
    }

    @Override
    public SystemAuditEvent logOperationalEvent(String eventType, String incidentId, String service, String severity, Map<String, Object> metadata) {
        return auditService.recordEvent(eventType, incidentId, service, severity, metadata);
    }
}
