package com.resq.evidence.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.resq.evidence.document.SystemAuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    @Value("${gcp.firestore.collection-name:system_events}")
    private String collectionName;

    @Value("${gcp.project-id:resq-enterprise-cloud}")
    private String projectId;

    private Firestore firestore;
    private boolean firestoreAvailable = false;
    private final List<SystemAuditEvent> localAuditBuffer = new CopyOnWriteArrayList<>();

    public AuditService() {
        try {
            this.firestore = FirestoreOptions.getDefaultInstance().getService();
            this.firestoreAvailable = true;
            log.info("Google Cloud Firestore client initialized for collection [{}]", collectionName);
        } catch (Exception e) {
            log.warn("Google Cloud Firestore client not initialized with GCP credentials (running in local audit buffer fallback mode): {}", e.getMessage());
            this.firestoreAvailable = false;
        }
    }

    public SystemAuditEvent recordEvent(String eventType, String incidentId, String service, String severity, Map<String, Object> metadata) {
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SystemAuditEvent event = new SystemAuditEvent(eventId, eventType, incidentId, service, severity, metadata);

        localAuditBuffer.add(event);

        if (firestoreAvailable && firestore != null) {
            try {
                firestore.collection(collectionName).document(eventId).set(event);
                log.info("Recorded event in Firestore [collection={}, eventId={}, type={}]", collectionName, eventId, eventType);
            } catch (Exception e) {
                log.warn("Firestore recordEvent failed ({}), saved in memory buffer.", e.getMessage());
            }
        } else {
            log.info("Recorded event locally [eventId={}, type={}]", eventId, eventType);
        }

        return event;
    }

    public List<SystemAuditEvent> getRecentEvents() {
        if (firestoreAvailable && firestore != null) {
            try {
                List<QueryDocumentSnapshot> documents = firestore.collection(collectionName)
                        .limit(50)
                        .get()
                        .get()
                        .getDocuments();

                List<SystemAuditEvent> events = new ArrayList<>();
                for (QueryDocumentSnapshot doc : documents) {
                    events.add(doc.toObject(SystemAuditEvent.class));
                }
                if (!events.isEmpty()) {
                    return events;
                }
            } catch (Exception e) {
                log.warn("Failed to query Firestore events: {}", e.getMessage());
            }
        }

        // Fallback to local buffer
        List<SystemAuditEvent> list = new ArrayList<>(localAuditBuffer);
        Collections.reverse(list);
        return list;
    }
}
