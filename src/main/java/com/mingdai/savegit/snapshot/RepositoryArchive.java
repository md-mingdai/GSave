package com.mingdai.savegit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 快照归档导出/导入。归档为 zip：commits/manifests/patches/packs + archive.json。
 */
public final class RepositoryArchive {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record ExportResult(Path file, int commits, int blobPacks) {
    }

    public record ImportResult(int commits, int blobs) {
    }

    private RepositoryArchive() {
    }

    public static ExportResult exportAll(Path worldRoot, Path targetZip) throws IOException {
        SnapshotRepository repo = new SnapshotRepository(worldRoot);
        repo.getBlobStore().flush();
        // 触发旧数据文件改名，保证导出目录里是新命名
        repo.getBlobStore().listHashes();
        List<Commit> commits = repo.listCommits();
        Path dataDir = repo.getSaveGitDir();

        Path parent = targetZip.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        int packCount = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            JsonObject meta = new JsonObject();
            meta.addProperty("format", RepositoryFormat.CURRENT);
            meta.addProperty("generator", com.mingdai.savegit.SaveGitMod.MOD_VERSION);
            meta.addProperty("created", System.currentTimeMillis());
            meta.addProperty("snapshots", commits.size());
            writeEntry(zip, "archive.json",
                    GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));

            Set<String> manifestIds = new HashSet<>();
            for (Commit commit : commits) {
                addFileIfExists(zip,
                        dataDir.resolve(SnapshotRepository.DIR_SNAPSHOTS)
                                .resolve(commit.id + ".json"),
                        SnapshotRepository.DIR_SNAPSHOTS + "/" + commit.id + ".json");
                addFileIfExists(zip,
                        dataDir.resolve(SnapshotRepository.DIR_CHANGES)
                                .resolve(commit.id + ".json"),
                        SnapshotRepository.DIR_CHANGES + "/" + commit.id + ".json");
                addFileIfExists(zip,
                        dataDir.resolve(SnapshotRepository.DIR_SCREENSHOTS)
                                .resolve(commit.id + ".png"),
                        SnapshotRepository.DIR_SCREENSHOTS + "/" + commit.id + ".png");
                manifestIds.add(commit.manifestId);
            }
            for (String manifestId : manifestIds) {
                addFileIfExists(zip,
                        dataDir.resolve(SnapshotRepository.DIR_FILELISTS)
                                .resolve(manifestId + ".json"),
                        SnapshotRepository.DIR_FILELISTS + "/" + manifestId + ".json");
            }

            Path packsDir = dataDir.resolve(SnapshotRepository.DIR_DATA);
            if (Files.isDirectory(packsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(packsDir)) {
                    for (Path pack : stream) {
                        String name = pack.getFileName().toString();
                        if (!Files.isRegularFile(pack)
                                || !(name.endsWith(".bin") || name.endsWith(".index"))) {
                            continue;
                        }
                        addFileIfExists(zip, pack,
                                SnapshotRepository.DIR_DATA + "/" + name);
                        if (name.endsWith(".bin")) {
                            packCount++;
                        }
                    }
                }
            }
        } finally {
            repo.getBlobStore().close();
        }
        return new ExportResult(targetZip, commits.size(), packCount);
    }

    public static ImportResult importArchive(Path worldRoot, Path zipFile) throws IOException {
        if (!Files.isRegularFile(zipFile)) {
            throw new IOException("archive not found: " + zipFile);
        }
        SnapshotRepository repo = new SnapshotRepository(worldRoot);
        repo.ensureWritableFormat();

        Path tempRoot = Files.createTempDirectory("gsave-import");
        Path tempData = tempRoot.resolve("gsave");
        try {
            extract(zipFile, tempData);

            SnapshotRepository sourceRepo = new SnapshotRepository(tempRoot);
            BlobStore source = sourceRepo.getBlobStore();
            BlobStore target = repo.getBlobStore();

            int importedCommits = 0;
            int importedBlobs = 0;
            try {
                Path commitsDir = tempData.resolve(SnapshotRepository.DIR_SNAPSHOTS);
                if (!Files.isDirectory(commitsDir)) {
                    throw new IOException("archive has no snapshots");
                }
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(commitsDir, "*.json")) {
                    for (Path path : stream) {
                        Commit commit = Commit.fromJson(
                                Files.readString(path, StandardCharsets.UTF_8));
                        if (commit == null) {
                            continue;
                        }
                        Manifest manifest = sourceRepo.getManifest(commit.manifestId);
                        if (manifest == null) {
                            continue;
                        }

                        Set<String> hashes = new HashSet<>();
                        RepositoryStats.collectHashes(manifest, hashes);
                        for (String hash : hashes) {
                            if (!target.exists(hash)) {
                                target.store(source.load(hash), hash);
                                importedBlobs++;
                            }
                        }
                        target.flush();
                        repo.saveManifest(manifest);
                        repo.saveCommit(commit);

                        Path patch = tempData.resolve(SnapshotRepository.DIR_CHANGES)
                                .resolve(commit.id + ".json");
                        if (Files.isRegularFile(patch)) {
                            Files.copy(patch,
                                    repo.getSaveGitDir().resolve(SnapshotRepository.DIR_CHANGES)
                                            .resolve(commit.id + ".json"),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                        Path shot = tempData.resolve(SnapshotRepository.DIR_SCREENSHOTS)
                                .resolve(commit.id + ".png");
                        if (Files.isRegularFile(shot)) {
                            Files.createDirectories(repo.getShotsDir());
                            Files.copy(shot,
                                    repo.getShotsDir().resolve(commit.id + ".png"),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                        importedCommits++;
                    }
                }
                target.flush();
            } finally {
                source.close();
                target.close();
            }
            return new ImportResult(importedCommits, importedBlobs);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void extract(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("illegal archive entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (OutputStream out = Files.newOutputStream(target)) {
                    copy(zip, out);
                }
            }
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static void addFileIfExists(ZipOutputStream zip, Path file, String entryName)
            throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = Files.newInputStream(file)) {
            copy(in, zip);
        }
        zip.closeEntry();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
