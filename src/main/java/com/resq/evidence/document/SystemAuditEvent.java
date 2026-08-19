package com.resq.evidence.document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class SystemAuditEvent {
    private String eventId;
    private String eventType;
    private String incidentId;
    private String service;
    private String severity;
    private String timestamp;
    private Map<String, Object> metadata = new HashMap<>();

    public SystemAuditEvent() {
        this.timestamp = Instant.now().toString();
    }

    public SystemAuditEvent(String eventId, String eventType, String incidentId, String service, String severity, Map<String, Object> metadata) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.incidentId = incidentId;
        this.service = service;
        this.severity = severity;
        this.timestamp = Instant.now().toString();
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
