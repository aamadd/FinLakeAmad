package com.amad.finlake.storage;

import com.amad.finlake.common.AmadProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.UploadObjectArgs;
import io.minio.messages.Item;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class MinioObjectStore implements AmadObjectStore {

    private final MinioClient client;
    private final String bucket;
    private final ObjectMapper mapper;

    public MinioObjectStore(AmadProperties props, ObjectMapper mapper) {
        this.bucket = props.minio().bucket();
        this.mapper = mapper;
        this.client = MinioClient.builder()
                .endpoint(props.minio().endpoint())
                .credentials(props.minio().accessKey(), props.minio().secretKey())
                .build();
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void putFile(String objectKey, Path localFile, String contentType) {
        try {
            client.uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .filename(localFile.toAbsolutePath().toString())
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload object: " + objectKey, ex);
        }
    }

    @Override
    public void putJson(String objectKey, Object payload) {
        try {
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
            try (var in = new ByteArrayInputStream(bytes)) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(in, bytes.length, -1)
                        .contentType("application/json; charset=" + StandardCharsets.UTF_8.name())
                        .build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload JSON manifest: " + objectKey, ex);
        }
    }

    @Override
    public void copyObject(String sourceKey, String destinationKey) {
        try {
            client.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(destinationKey)
                    .source(CopySource.builder().bucket(bucket).object(sourceKey).build())
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to promote object: " + destinationKey, ex);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to delete staging object: " + objectKey, ex);
        }
    }

    @Override
    public List<String> listKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());
            for (Result<Item> r : results) {
                keys.add(r.get().objectName());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to list objects under prefix: " + prefix, ex);
        }
        return keys;
    }
}
