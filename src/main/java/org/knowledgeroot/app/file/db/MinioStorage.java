package org.knowledgeroot.app.file.db;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioStorage implements FileStorage {
    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorage(
            @Value("${minio.url}") String url,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket}") String bucket) {
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();

        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize Minio storage.", ex);
        }
    }

    @Override
    public void store(String hash, InputStream inputStream) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .stream(inputStream, -1, 10485760)
                            .build());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to store file in Minio.", ex);
        }
    }

    @Override
    public InputStream retrieve(String hash) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .build());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to retrieve file from Minio.", ex);
        }
    }

    @Override
    public void delete(String hash) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .build());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to delete file from Minio.", ex);
        }
    }

    @Override
    public boolean exists(String hash) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .build());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
