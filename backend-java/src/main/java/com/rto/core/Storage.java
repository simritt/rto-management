package com.rto.core;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Document storage abstraction. MySQL keeps only `file_path`; the bytes live behind this interface so local disk can
 * be replaced by S3/GCS/Azure by providing another StorageProvider bean.
 */
public interface Storage {
    /** Persist content and return the opaque relative path stored in the database. */
    String save(String folder, String extension, byte[] content);

    byte[] read(String path);

    boolean exists(String path);

    @Component
    class Local implements Storage {
        private final Path root;

        public Local(Settings settings) {
            this.root = Path.of(settings.storageDir()).toAbsolutePath().normalize();
        }

        private Path resolve(String rel) {
            Path full = root.resolve(rel).normalize();
            if (!full.startsWith(root)) throw new IllegalArgumentException("path escapes storage root");
            return full;
        }

        @Override
        public String save(String folder, String extension, byte[] content) {
            String rel = folder.replaceAll("^/+|/+$", "") + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
            try {
                Path target = resolve(rel);   // server-generated name; the client's file name is never used
                Files.createDirectories(target.getParent());
                Files.write(target, content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return rel;
        }

        @Override
        public byte[] read(String path) {
            try {
                return Files.readAllBytes(resolve(path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean exists(String path) {
            try {
                return Files.isRegularFile(resolve(path));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
