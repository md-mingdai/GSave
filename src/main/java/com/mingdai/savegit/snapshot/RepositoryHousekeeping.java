package com.mingdai.savegit.snapshot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 仓库文件清理：无主截图/补丁/清单与残留临时文件。
 */
public final class RepositoryHousekeeping {
    private RepositoryHousekeeping() {
    }

    public static long countOrphanShots(Path saveGitDir, Set<String> commitIds) {
        return countMatching(saveGitDir.resolve("shots"), ".png", commitIds);
    }

    public static long countOrphanPatches(Path saveGitDir, Set<String> commitIds) {
        return countMatching(saveGitDir.resolve("patches"), ".json", commitIds);
    }

    public static long countOrphanManifests(Path saveGitDir, Set<String> manifestIds) {
        return countMatching(saveGitDir.resolve("manifests"), ".json", manifestIds);
    }

    public static long countTempFiles(Path saveGitDir) {
        return listTempFiles(saveGitDir).count();
    }

    public static int deleteOrphanShots(Path saveGitDir, Set<String> commitIds) {
        return deleteMatching(saveGitDir.resolve("shots"), ".png", commitIds);
    }

    public static int deleteOrphanPatches(Path saveGitDir, Set<String> commitIds) {
        return deleteMatching(saveGitDir.resolve("patches"), ".json", commitIds);
    }

    public static int deleteOrphanManifests(Path saveGitDir, Set<String> manifestIds) {
        return deleteMatching(saveGitDir.resolve("manifests"), ".json", manifestIds);
    }

    public static int deleteTempFiles(Path saveGitDir) {
        int deleted = 0;
        try (Stream<Path> stream = listTempFiles(saveGitDir)) {
            for (Path path : stream.toList()) {
                try {
                    Files.deleteIfExists(path);
                    deleted++;
                } catch (IOException ignored) {
                }
            }
        }
        return deleted;
    }

    private static long countMatching(Path dir, String suffix, Set<String> known) {
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        long count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (isOrphan(path, suffix, known)) {
                    count++;
                }
            }
        } catch (IOException ignored) {
        }
        return count;
    }

    private static int deleteMatching(Path dir, String suffix, Set<String> known) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (!isOrphan(path, suffix, known)) {
                    continue;
                }
                try {
                    Files.deleteIfExists(path);
                    deleted++;
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return deleted;
    }

    private static boolean isOrphan(Path path, String suffix, Set<String> known) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString();
        if (!name.endsWith(suffix)) {
            return false;
        }
        String id = name.substring(0, name.length() - suffix.length());
        if (id.startsWith("pending-")) {
            return true;
        }
        return !known.contains(id);
    }

    private static Stream<Path> listTempFiles(Path saveGitDir) {
        if (!Files.isDirectory(saveGitDir)) {
            return Stream.empty();
        }
        try {
            return Files.walk(saveGitDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".tmp"))
                    .toList().stream();
        } catch (IOException e) {
            return Stream.empty();
        }
    }
}
