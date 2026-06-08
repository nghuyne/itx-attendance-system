package com.itx.attendance.service;

import com.itx.attendance.exception.BusinessException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PhotoService {

    public record PhotoData(byte[] bytes, String contentType) {}

    private static final int MAX_PHOTO_BYTES = 512_000;
    private static final String DATA_URI_JPEG = "data:image/jpeg;base64,";
    private static final String DATA_URI_PNG  = "data:image/png;base64,";

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    // Returns the objectKey on success (P:D1 — caller stores key, not presigned URL)
    @Async("taskExecutor")
    public CompletableFuture<String> uploadPhotoAsync(byte[] imageBytes, String objectKey, String contentType) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                    .contentType(contentType)
                    .build()
            );
            return CompletableFuture.completedFuture(objectKey);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry(1, TimeUnit.HOURS)
                    .build()
            );
        } catch (Exception e) {
            throw new BusinessException(
                "Cannot generate presigned URL", HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_URL_FAILED");
        }
    }

    public PhotoData decodeBase64Photo(String photoBase64) {
        if (photoBase64 == null || photoBase64.isBlank()) {
            throw new BusinessException("Photo is required", HttpStatus.BAD_REQUEST, "PHOTO_REQUIRED");
        }

        String contentType = "image/jpeg";
        String base64Data = photoBase64;
        if (base64Data.startsWith(DATA_URI_JPEG)) {
            base64Data = base64Data.substring(DATA_URI_JPEG.length());
        } else if (base64Data.startsWith(DATA_URI_PNG)) {
            contentType = "image/png";
            base64Data = base64Data.substring(DATA_URI_PNG.length());
        } else if (base64Data.startsWith("data:")) {
            int commaIdx = base64Data.indexOf(',');
            if (commaIdx != -1) base64Data = base64Data.substring(commaIdx + 1);
        }

        base64Data = base64Data.strip();
        if (base64Data.isEmpty()) {
            throw new BusinessException("Invalid photo data", HttpStatus.BAD_REQUEST, "INVALID_PHOTO_DATA");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid photo data", HttpStatus.BAD_REQUEST, "INVALID_PHOTO_DATA");
        }

        if (bytes.length > MAX_PHOTO_BYTES) {
            throw new BusinessException(
                "Photo exceeds 500KB limit", HttpStatus.BAD_REQUEST, "PHOTO_TOO_LARGE");
        }

        return new PhotoData(bytes, contentType);
    }
}
