package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.mca.RawMca;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 把世界存档写入内容寻址仓库。四条触发路径共用一把锁，保证线性历史不被破坏。
 */
public final class WorldSnapshotter {
    private static final Object SNAPSHOT_LOCK = new Object();
    private static final McaWorldScanner SCANNER = new McaWorldScanner();

    private final SnapshotRepository repo;

    public WorldSnapshotter(SnapshotRepository repo) {
        this.repo = repo;
    }

    public record Settings(boolean region, boolean entities, boolean poi, boolean levelDat,
                           boolean playerdata, int maxSnapshots, String worldName, String mcVersion) {
    }

    public Commit takeSnapshot(String message, Settings settings) throws IOException {
        synchronized (SNAPSHOT_LOCK) {
            return takeSnapshotLocked(message, settings);
        }
    }

    private Commit takeSnapshotLocked(String message, Settings settings) throws IOException {
        try {
        repo.ensureWritableFormat();
        Path worldRoot = repo.getWorldRoot();
        Manifest manifest = new Manifest();
        Set<String> storedWholeFiles = new HashSet<>();

        List<McaWorldScanner.McaFileEntry> entries =
                SCANNER.scan(worldRoot, settings.region(), settings.entities(), settings.poi());
        for (McaWorldScanner.McaFileEntry entry : entries) {
            snapshotMcaFile(manifest, worldRoot, entry, storedWholeFiles);
        }

        if (settings.levelDat()) {
            Path levelDat = worldRoot.resolve("level.dat");
            if (Files.isRegularFile(levelDat)) {
                snapshotWholeFile(manifest, worldRoot, levelDat, null, storedWholeFiles);
            }
        }
        if (settings.playerdata()) {
            snapshotPlayerData(manifest, worldRoot, storedWholeFiles);
        }

        repo.getBlobStore().flush();
        String manifestId = repo.saveManifest(manifest);
        String parentId = repo.readHead();
        Commit parent = parentId == null ? null : repo.getCommit(parentId);
        if (parent == null) {
            parentId = null;
        }
        Manifest oldManifest = parent == null ? null : repo.getManifest(parent.manifestId);

        Commit commit = Commit.create(parentId, manifestId, System.currentTimeMillis(),
                message, settings.worldName(), settings.mcVersion());

        Patch patch = PatchGenerator.generate(commit.id, parentId, oldManifest, manifest);
        repo.savePatch(patch);
        repo.saveCommit(commit);
        repo.writeHead(commit.id);
        repo.cleanupOldSnapshots(settings.maxSnapshots());
        return commit;
        } finally {
            repo.getBlobStore().close();
        }
    }

    private void snapshotMcaFile(Manifest manifest, Path worldRoot,
                                 McaWorldScanner.McaFileEntry entry, Set<String> storedWholeFiles)
            throws IOException {
        long size = Files.size(entry.filePath());

        // 0 字节占位文件或含外部 .mcc 大区块 → 整文件回退，并连带所有同级 .mcc
        if (size < RawMca.HEADER_BYTES || RawMca.hasExternalChunks(entry.filePath())) {
            snapshotWholeFile(manifest, worldRoot, entry.filePath(), entry.dimensionId(), storedWholeFiles);
            for (Path mcc : McaWorldScanner.listExternalMccFiles(entry.filePath())) {
                snapshotWholeFile(manifest, worldRoot, mcc, entry.dimensionId(), storedWholeFiles);
            }
            return;
        }

        Map<Integer, byte[]> chunkRecords = RawMca.readChunks(entry.filePath());
        Map<Integer, String> chunkHashes = new TreeMap<>();
        for (Map.Entry<Integer, byte[]> chunk : chunkRecords.entrySet()) {
            chunkHashes.put(chunk.getKey(), repo.getBlobStore().store(chunk.getValue()));
        }
        manifest.addMcaFile(entry.relativePath(), entry.dimensionId(),
                entry.type().folderName, entry.regionX(), entry.regionZ(), chunkHashes);
    }

    private void snapshotPlayerData(Manifest manifest, Path worldRoot,
                                    Set<String> storedWholeFiles) throws IOException {
        Path playerdata = worldRoot.resolve("playerdata");
        if (!Files.isDirectory(playerdata)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerdata, "*.dat")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (Path file : files) {
            snapshotWholeFile(manifest, worldRoot, file, null, storedWholeFiles);
        }
    }

    private void snapshotWholeFile(Manifest manifest, Path worldRoot, Path file,
                                   String dimension, Set<String> storedWholeFiles) throws IOException {
        String relative = worldRoot.relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        if (!storedWholeFiles.add(relative)) {
            return;
        }
        String hash = repo.getBlobStore().store(file);
        manifest.addWholeFile(relative, dimension, hash);
    }
}
