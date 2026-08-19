package com.resq.evidence.repository;

import com.resq.evidence.document.EvidenceMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceRepository extends MongoRepository<EvidenceMetadata, String> {
    List<EvidenceMetadata> findByIncidentIdOrderByUploadedAtDesc(String incidentId);
    long countByIncidentId(String incidentId);
}
