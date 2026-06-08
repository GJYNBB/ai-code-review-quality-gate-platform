package com.acrqg.platform.scanner.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Auto-closeable checked-out source workspace. */
public final class MaterializedSource implements AutoCloseable {

    private final Path workdir;
    private final Path cleanupRoot;

    MaterializedSource(Path workdir, Path cleanupRoot) {
        this.workdir = workdir;
        this.cleanupRoot = cleanupRoot;
    }

    public Path workdir() {
        return workdir;
    }

    @Override
    public void close() {
        Path root = cleanupRoot == null ? workdir : cleanupRoot;
        if (root == null) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best effort cleanup
                        }
                    });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
