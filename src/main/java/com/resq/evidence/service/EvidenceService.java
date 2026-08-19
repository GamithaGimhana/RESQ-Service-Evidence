package com.resq.evidence.service;

import com.resq.evidence.document.EvidenceMetadata;
import com.resq.evidence.document.SystemAuditEvent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface EvidenceService {

    EvidenceMetadata uploadEvidence(String incidentId, MultipartFile file, String description, String uploadedBy);

    List<EvidenceMetadata> getEvidenceByIncident(String incidentId);

    EvidenceMetadata getEvidenceById(String id);

    EvidenceDownload downloadEvidence(String id);

    void deleteEvidence(String id);

    List<SystemAuditEvent> getAuditEvents();

    SystemAuditEvent logOperationalEvent(String eventType, String incidentId, String service, String severity, Map<String, Object> metadata);

    class EvidenceDownload {
        private final byte[] data;
        private final String fileName;
        private final String contentType;

        public EvidenceDownload(byte[] data, String fileName, String contentType) {
            this.data = data;
            this.fileName = fileName;
            this.contentType = contentType;
        }

        public byte[] getData() {
            return data;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
