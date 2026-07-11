package com.school.app.common.storage;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService newService() {
        return new LocalFileStorageService(tempDir.toString());
    }

    @Test
    void storesAndReloadsAFile() {
        LocalFileStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile(
                "file", "homework.pdf", "application/pdf", "hello world".getBytes(StandardCharsets.UTF_8));

        String key = service.store(file, "homework-submissions");

        assertThat(key).startsWith("homework-submissions/").endsWith(".pdf");
        assertThat(new String(service.load(key), StandardCharsets.UTF_8)).isEqualTo("hello world");
    }

    @Test
    void rejectsAnEmptyFile() {
        LocalFileStorageService service = newService();
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.store(empty, "homework-submissions"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void loadingAMissingKeyThrowsNotFound() {
        LocalFileStorageService service = newService();

        assertThatThrownBy(() -> service.load("homework-submissions/does-not-exist.pdf"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsAPathTraversalKey() {
        LocalFileStorageService service = newService();

        assertThatThrownBy(() -> service.load("../../etc/passwd"))
                .isInstanceOf(BadRequestException.class);
    }
}
