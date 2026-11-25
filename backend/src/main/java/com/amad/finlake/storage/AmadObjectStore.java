package com.amad.finlake.storage;

import java.nio.file.Path;
import java.util.List;

/** SOLID abstraction over object storage so ingestion/catalog do not couple directly to MinIO SDK. */
public interface AmadObjectStore {

    String bucket();

    void putFile(String objectKey, Path localFile, String contentType);

    void putJson(String objectKey, Object payload);

    void copyObject(String sourceKey, String destinationKey);

    void deleteObject(String objectKey);

    List<String> listKeys(String prefix);
}
