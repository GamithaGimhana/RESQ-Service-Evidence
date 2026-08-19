package com.resq.evidence.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    @Value("${gcp.storage.bucket-name:resq-production-evidence}")
    private String bucketName;

    @Value("${gcp.project-id:resq-enterprise-cloud}")
    private String projectId;

    private Storage storage;
    private boolean gcsAvailable = false;

    public StorageService() {
        try {
            this.storage = StorageOptions.getDefaultInstance().getService();
            this.gcsAvailable = true;
            log.info("Google Cloud Storage client initialized successfully for bucket [{}]", bucketName);
        } catch (Exception e) {
            log.warn("Google Cloud Storage client not initialized with GCP credentials (running in local filesystem fallback mode): {}", e.getMessage());
            this.gcsAvailable = false;
        }
    }

    public UploadResult upload(String incidentId, String originalFilename, String contentType, byte[] data) {
        String cleanFilename = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_") : "file";
        String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + cleanFilename;
        String objectName = "incidents/" + incidentId + "/files/" + uniqueName;

        if (gcsAvailable && storage != null) {
            try {
                BlobId blobId = BlobId.of(bucketName, objectName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType(contentType)
                        .build();

                storage.create(blobInfo, data);
                String fileUrl = "https://storage.googleapis.com/" + bucketName + "/" + objectName;
                log.info("Successfully uploaded object to GCS: gs://{}/{}", bucketName, objectName);
                return new UploadResult(bucketName, objectName, uniqueName, fileUrl);
            } catch (Exception e) {
                log.warn("GCS upload failed ({}), falling back to local storage...", e.getMessage());
            }
        }

        // Local filesystem fallback
        try {
            Path localDir = Paths.get(System.getProperty("user.home"), ".resq", "storage", "incidents", incidentId);
            Files.createDirectories(localDir);
            Path filePath = localDir.resolve(uniqueName);
            Files.write(filePath, data);
            String fileUrl = "/api/v1/evidence/local/" + incidentId + "/" + uniqueName;
            log.info("Stored file locally at: {}", filePath);
            return new UploadResult("local-resq-storage", objectName, uniqueName, fileUrl);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to persist file in both GCS and local storage: " + ex.getMessage(), ex);
        }
    }

    public byte[] download(String bucket, String objectName) {
        if (gcsAvailable && storage != null && !"local-resq-storage".equals(bucket)) {
            try {
                BlobId blobId = BlobId.of(bucket, objectName);
                Blob blob = storage.get(blobId);
                if (blob != null && blob.exists()) {
                    return blob.getContent();
                }
            } catch (Exception e) {
                log.warn("Failed to download from GCS: {}", e.getMessage());
            }
        }

        // Fallback to local storage
        try {
            String[] parts = objectName.split("/");
            String incidentId = parts.length >= 2 ? parts[1] : "unknown";
            String filename = parts.length >= 4 ? parts[3] : parts[parts.length - 1];
            Path localPath = Paths.get(System.getProperty("user.home"), ".resq", "storage", "incidents", incidentId, filename);
            if (Files.exists(localPath)) {
                return Files.readAllBytes(localPath);
            }
        } catch (Exception e) {
            log.error("Local file download error: {}", e.getMessage());
        }

        throw new RuntimeException("File not found in storage: " + objectName);
    }

    public void delete(String bucket, String objectName) {
        if (gcsAvailable && storage != null && !"local-resq-storage".equals(bucket)) {
            try {
                BlobId blobId = BlobId.of(bucket, objectName);
                storage.delete(blobId);
                log.info("Deleted GCS object: gs://{}/{}", bucket, objectName);
                return;
            } catch (Exception e) {
                log.warn("GCS delete failed: {}", e.getMessage());
            }
        }

        // Local deletion
        try {
            String[] parts = objectName.split("/");
            String incidentId = parts.length >= 2 ? parts[1] : "unknown";
            String filename = parts.length >= 4 ? parts[3] : parts[parts.length - 1];
            Path localPath = Paths.get(System.getProperty("user.home"), ".resq", "storage", "incidents", incidentId, filename);
            Files.deleteIfExists(localPath);
        } catch (Exception e) {
            log.warn("Local file deletion failed: {}", e.getMessage());
        }
    }

    public static class UploadResult {
        private final String bucket;
        private final String objectName;
        private final String fileName;
        private final String fileUrl;

        public UploadResult(String bucket, String objectName, String fileName, String fileUrl) {
            this.bucket = bucket;
            this.objectName = objectName;
            this.fileName = fileName;
            this.fileUrl = fileUrl;
        }

        public String getBucket() {
            return bucket;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFileUrl() {
            return fileUrl;
        }
    }
}
