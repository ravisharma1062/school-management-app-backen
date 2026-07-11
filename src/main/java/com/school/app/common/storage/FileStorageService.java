package com.school.app.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Single storage abstraction for every feature that needs to persist an uploaded file
 * (homework submissions, book covers, etc.) — not a separate upload handler per feature.
 * The default implementation writes to local disk; swap in an S3/Cloudinary-backed
 * implementation for production by providing another {@code @Service} bean and
 * removing {@link LocalFileStorageService}'s {@code @Primary} (or excluding it).
 */
public interface FileStorageService {

    /** Stores the file under the given subdirectory and returns an opaque key to pass to {@link #load}. */
    String store(MultipartFile file, String subdirectory);

    /** Loads a previously stored file's raw bytes. */
    byte[] load(String key);
}
