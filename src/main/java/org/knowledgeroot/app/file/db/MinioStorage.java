package org.knowledgeroot.app.file.db;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.knowledgeroot.app.file.domain.StorageException;
import org.knowledgeroot.app.file.domain.StoredFileNotFoundException;

import java.io.InputStream;

/**
 * Minio storage implementation of the file storage.
 */
class MinioStorage implements FileStorage, AutoCloseable {
    private final MinioClient minioClient;
    private final String bucket;
    private final okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .writeTimeout(java.time.Duration.ofSeconds(60))
            .callTimeout(java.time.Duration.ofMinutes(2)).build();

    public MinioStorage(
            @Value("${minio.url}") String url,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket}") String bucket) {
        if (url == null || url.isBlank() || accessKey == null || accessKey.isBlank()
                || secretKey == null || secretKey.isBlank() || bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("MinIO requires URL, access key, secret key and bucket");
        }
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();

        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            close();
            throw new StorageException("Failed to initialize Minio storage.", ex);
        }
    }

    @Override
    public void store(String hash, InputStream inputStream) {
        StorageKey.validate(hash);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .stream(inputStream, -1L, 10485760L)
                            .build());
        } catch (Exception ex) {
            throw new StorageException("Failed to store file in Minio.", ex);
        }
    }

    @Override
    public InputStream retrieve(String hash) {
        StorageKey.validate(hash);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .build());
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) throw new StoredFileNotFoundException(ex);
            throw new StorageException("Failed to retrieve file from Minio.", ex);
        } catch (Exception ex) {
            throw new StorageException("Failed to retrieve file from Minio.", ex);
        }
    }

    @Override
    public void delete(String hash) {
        StorageKey.validate(hash);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .build());
        } catch (Exception ex) {
            throw new StorageException("Failed to delete file from Minio.", ex);
        }
    }

    @Override
    public boolean exists(String hash) {
        StorageKey.validate(hash);
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(hash)
                            .build());
            return true;
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) return false;
            throw new StorageException("Could not inspect stored object", ex);
        } catch (Exception ex) {
            throw new StorageException("Could not inspect stored object", ex);
        }
    }

    @Override public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
