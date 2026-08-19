package com.resq.evidence.service;

import com.resq.evidence.document.EvidenceMetadata;
import com.resq.evidence.document.SystemAuditEvent;
import com.resq.evidence.repository.EvidenceRepository;
import com.resq.evidence.service.impl.EvidenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTests {

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private AuditService auditService;

    private EvidenceService evidenceService;

    @BeforeEach
    void setUp() {
        evidenceService = new EvidenceServiceImpl(evidenceRepository, storageService, auditService);
    }

    @Test
    void testUploadEvidenceSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "flood_damage.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        when(storageService.upload(eq("INC-001"), eq("flood_damage.jpg"), eq("image/jpeg"), any(byte[].class)))
                .thenReturn(new StorageService.UploadResult("resq-production-evidence", "incidents/INC-001/files/flood_damage.jpg", "flood_damage.jpg", "http://storage/flood.jpg"));

        when(evidenceRepository.save(any(EvidenceMetadata.class))).thenAnswer(inv -> {
            EvidenceMetadata meta = inv.getArgument(0);
            meta.setId("evd-1");
            return meta;
        });

        EvidenceMetadata result = evidenceService.uploadEvidence("INC-001", file, "Submerged road", "responder@resq.gov");

        assertNotNull(result);
        assertEquals("evd-1", result.getId());
        assertEquals("INC-001", result.getIncidentId());
        assertEquals("flood_damage.jpg", result.getFileName());
        verify(auditService).recordEvent(eq("EVIDENCE_UPLOADED"), eq("INC-001"), eq("evidence-service"), eq("INFO"), any());
    }

    @Test
    void testUploadEmptyFileFails() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThrows(IllegalArgumentException.class, () ->
                evidenceService.uploadEvidence("INC-001", emptyFile, "Empty", "user"));
    }

    @Test
    void testDownloadEvidence() {
        EvidenceMetadata meta = new EvidenceMetadata();
        meta.setId("evd-1");
        meta.setBucket("resq-production-evidence");
        meta.setObjectName("incidents/INC-001/files/flood.jpg");
        meta.setOriginalFileName("flood.jpg");
        meta.setContentType("image/jpeg");

        when(evidenceRepository.findById("evd-1")).thenReturn(Optional.of(meta));
        when(storageService.download("resq-production-evidence", "incidents/INC-001/files/flood.jpg"))
                .thenReturn("image-bytes".getBytes());

        EvidenceService.EvidenceDownload download = evidenceService.downloadEvidence("evd-1");

        assertNotNull(download);
        assertEquals("flood.jpg", download.getFileName());
        assertEquals("image/jpeg", download.getContentType());
        assertEquals("image-bytes", new String(download.getData()));
    }

    @Test
    void testGetAuditEvents() {
        SystemAuditEvent evt = new SystemAuditEvent("EVT-1", "INCIDENT_CREATED", "INC-001", "incident-service", "CRITICAL", null);
        when(auditService.getRecentEvents()).thenReturn(Arrays.asList(evt));

        List<SystemAuditEvent> list = evidenceService.getAuditEvents();
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("INCIDENT_CREATED", list.get(0).getEventType());
    }
}
