package com.resq.evidence.controller;

import com.resq.evidence.document.EvidenceMetadata;
import com.resq.evidence.document.SystemAuditEvent;
import com.resq.evidence.service.EvidenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final com.resq.evidence.service.StorageService storageService;

    @Autowired
    public EvidenceController(EvidenceService evidenceService, com.resq.evidence.service.StorageService storageService) {
        this.evidenceService = evidenceService;
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceMetadata> uploadEvidence(
            @RequestParam("incidentId") String incidentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uploader = userId != null ? userId : "field-responder";
        EvidenceMetadata saved = evidenceService.uploadEvidence(incidentId, file, description, uploader);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/incidents/{incidentId}")
    public ResponseEntity<List<EvidenceMetadata>> getEvidenceByIncident(@PathVariable String incidentId) {
        List<EvidenceMetadata> list = evidenceService.getEvidenceByIncident(incidentId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvidenceMetadata> getEvidenceById(@PathVariable String id) {
        EvidenceMetadata metadata = evidenceService.getEvidenceById(id);
        return ResponseEntity.ok(metadata);
    }

    @GetMapping({"/local/{incidentId}/{filename}", "/file/{incidentId}/{filename}"})
    public ResponseEntity<ByteArrayResource> getLocalEvidenceFile(
            @PathVariable("incidentId") String incidentId,
            @PathVariable("filename") String filename) {
        byte[] data;
        try {
            data = storageService.download("resq-production-evidence", "incidents/" + incidentId + "/files/" + filename);
        } catch (Exception e) {
            data = storageService.download("local-resq-storage", "incidents/" + incidentId + "/files/" + filename);
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            mediaType = MediaType.IMAGE_JPEG;
        } else if (lower.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (lower.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        } else if (lower.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        }

        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(data.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadEvidence(@PathVariable String id) {
        EvidenceService.EvidenceDownload download = evidenceService.downloadEvidence(id);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.getContentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(download.getContentType());
            } catch (Exception ignored) {
            }
        }

        ByteArrayResource resource = new ByteArrayResource(download.getData());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.getData().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvidence(@PathVariable String id) {
        evidenceService.deleteEvidence(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    public ResponseEntity<List<SystemAuditEvent>> getAuditEvents() {
        List<SystemAuditEvent> events = evidenceService.getAuditEvents();
        return ResponseEntity.ok(events);
    }

    @PostMapping("/audit")
    public ResponseEntity<SystemAuditEvent> createAuditEvent(
            @RequestParam("eventType") String eventType,
            @RequestParam(value = "incidentId", required = false) String incidentId,
            @RequestParam(value = "service", defaultValue = "external") String service,
            @RequestParam(value = "severity", defaultValue = "INFO") String severity,
            @RequestBody(required = false) Map<String, Object> metadata) {
        SystemAuditEvent event = evidenceService.logOperationalEvent(eventType, incidentId, service, severity, metadata);
        return new ResponseEntity<>(event, HttpStatus.CREATED);
    }
}
