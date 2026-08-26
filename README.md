# RESQ — Evidence Storage & Firestore Audit Microservice (`resq-evidence-service`)

## Student & Assessment Details
- **Student Name:** H.V.Gamitha Gimhana Jayasanka
- **Student ID / Number:** 241711007
- **Slack Handle:** Gamitha Gimhana
- **GCP Project ID:** `resq-enterprise-cloud-01`
- **Course:** ITS 2130 — Enterprise Cloud Architecture

---

## 1. Project Description
`resq-evidence-service` manages emergency incident digital evidence (disaster photos, damage inspection reports, PDFs) and immutable operational audit trails. It executes real multipart file uploads streaming binary payloads directly to **Google Cloud Storage** buckets, indexes metadata in **MongoDB**, provides inline public media streaming, and broadcasts system events to **Google Cloud Firestore**.

---

## 2. Technology Stack & Multi-Cloud Architecture
- **Runtime:** Java 25 / 21 LTS
- **Framework:** Spring Boot 3.3.5, Spring Data MongoDB
- **Object Storage (Mandatory):** Google Cloud Storage (`resq-production-evidence` bucket)
- **Object Key Layout:** `incidents/{incidentId}/files/{uniqueFileName}`
- **Metadata Database:** MongoDB (`resq_evidence` database, `evidence` collection)
- **Event Ledger / Audit (Mandatory):** Google Cloud Firestore (`system_events` collection)
- **Service Discovery:** Netflix Eureka Client
- **Process Management:** PM2 on GCP Compute Engine Multi-Zone MIG

---

## 3. API Endpoints Specification
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/evidence/upload` | Multipart file upload (max 10MB, GCS binary stream + MongoDB metadata) |
| `GET` | `/api/v1/evidence/incidents/{incidentId}` | List all evidence records for an incident |
| `GET` | `/api/v1/evidence/{id}` | Retrieve evidence metadata details |
| `GET` | `/api/v1/evidence/file/{incidentId}/{filename}` | Stream and view media inline without auth headers |
| `GET` | `/api/v1/evidence/{id}/download` | Stream and download file binary from Google Cloud Storage |
| `DELETE` | `/api/v1/evidence/{id}` | Delete file from GCS and remove metadata from MongoDB |
| `GET` | `/api/v1/evidence/audit` | Fetch real-time audit ledger events from Google Cloud Firestore |
| `POST` | `/api/v1/evidence/audit` | Publish operational lifecycle events |

---

## 4. Supported Operational Events (Firestore)
- `INCIDENT_CREATED`
- `INCIDENT_UPDATED`
- `TEAM_ASSIGNED`
- `RESOURCE_ALLOCATED`
- `RESOURCE_RELEASED`
- `EVIDENCE_UPLOADED`
- `EVIDENCE_DELETED`
- `INCIDENT_RESOLVED`
- `SERVICE_FAILURE`
- `SERVICE_RECOVERED`

---

## 5. Setup & Getting Started

### Local Development
```bash
# Compile and run
mvn clean spring-boot:run

# Run unit tests
mvn clean test
```

### Production Execution (PM2)
```bash
# Package
mvn clean package -DskipTests

# PM2 Process launch
pm2 start /opt/resq/apps/resq-evidence-service-1.0.0.jar --name "resq-evidence-service"

# Save PM2 state
pm2 save
pm2 startup systemd
```
