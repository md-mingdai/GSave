package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.SaveGitMod;
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
                           boolean playerdata, int maxSnapshots, boolean skipUnchanged,
                           boolean protectManual, String worldName, String mcVersion,
                           boolean datapacks, boolean otherFiles, boolean modEnvironment,
                           long maxFileBytes, long maxOtherBytes) {
    }

    /** 自动快照检测到与 HEAD 完全一致时抛出，用于跳过重复提交。 */
    public static final class UnchangedSnapshotException extends IOException {
        public UnchangedSnapshotException() {
            super("snapshot unchanged");
        }
    }

    @FunctionalInterface
    public interface LockedTask {
        void run() throws Exception;
    }

    /** 与快照创建共用一把锁，维护任务（自检/清理）据此串行执行。 */
    public static void runLocked(LockedTask task) throws Exception {
        synchronized (SNAPSHOT_LOCK) {
            task.run();
        }
    }

    public Commit takeSnapshot(String message, Settings settings) throws IOException {
        synchronized (SNAPSHOT_LOCK) {
            return takeSnapshotLocked(message, settings);
        }
    }

    private Commit takeSnapshotLocked(String message, Settings settings) throws IOException {
        try {
        repo.ensureWritableFormat();
        long startedAt = System.currentTimeMillis();
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

        // Beta：第三方模组写入世界目录的其他文件与数据包
        if (settings.datapacks() || settings.otherFiles()) {
            snapshotExtraFiles(manifest, worldRoot, settings, storedWholeFiles);
        }
        // Beta：记录模组环境（不阻塞快照，失败时为空）
        if (settings.modEnvironment()) {
            WorldModEnvironment environment = WorldModEnvironment.capture();
            if (!environment.isEmpty()) {
                manifest.modEnvironment = environment.toJson();
                manifest.modFingerprint = environment.fingerprint();
            }
        }

        repo.getBlobStore().flush();
        String manifestId = repo.saveManifest(manifest);
        String parentId = repo.readHead();
        Commit parent = parentId == null ? null : repo.getCommit(parentId);
        if (parent == null) {
            parentId = null;
        }
        Manifest oldManifest = parent == null ? null : repo.getManifest(parent.manifestId);

        if (settings.skipUnchanged() && parent != null
                && manifestId.equals(parent.manifestId)) {
            throw new UnchangedSnapshotException();
        }

        Commit commit = Commit.create(parentId, manifestId, System.currentTimeMillis(),
                message, settings.worldName(), settings.mcVersion());
        commit.durationMs = Math.max(0L, System.currentTimeMillis() - startedAt);
        commit.environmentFingerprint = manifest.modFingerprint == null ? "" : manifest.modFingerprint;

        Patch patch = PatchGenerator.generate(commit.id, parentId, oldManifest, manifest);
        repo.savePatch(patch);
        repo.saveCommit(commit);
        repo.writeHead(commit.id);
        int removed = repo.cleanupOldSnapshots(settings.maxSnapshots(), settings.protectManual());
        if (removed > 0) {
            // 自动清理删除了旧提交：后台回收不再被引用的数据块
            RepositoryGc.collectQuietAsync(worldRoot);
        }
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

    /**
     * Beta：把数据包与模组写入世界目录的其他文件按内容寻址整文件保存。
     * 只搬运字节、不解析第三方格式，因此对任何模组都兼容。
     */
    private void snapshotExtraFiles(Manifest manifest, Path worldRoot, Settings settings,
                                    Set<String> storedWholeFiles) throws IOException {
        Path root = worldRoot.toAbsolutePath().normalize();
        Path saveGitDir = SnapshotRepository.dataDir(worldRoot);
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> relativePath(root, path)));

        long budget = Math.max(0L, settings.maxOtherBytes());
        long used = 0L;
        int stored = 0;
        int skipped = 0;
        boolean budgetReached = false;

        try (WorldFileHashCache cache = WorldFileHashCache.open(saveGitDir)) {
            for (Path file : files) {
                String relative = relativePath(root, file);
                String lower = relative.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".mca") || lower.endsWith(".mcc")) {
                    continue; // 已由区块扫描或整文件回退处理
                }
                if (WorldFileGroups.isExcluded(relative) || storedWholeFiles.contains(relative)) {
                    continue;
                }
                if (WorldFileGroups.isDatapack(relative)) {
                    if (!settings.datapacks()) {
                        continue;
                    }
                } else if (!settings.otherFiles()) {
                    continue;
                }

                long size = Files.size(file);
                if (settings.maxFileBytes() > 0 && size > settings.maxFileBytes()) {
                    SaveGitMod.LOGGER.warn("跳过超过单文件上限的模组文件: {} ({} 字节)", relative, size);
                    skipped++;
                    continue;
                }
                if (budgetReached || used + size > budget) {
                    budgetReached = true;
                    skipped++;
                    continue;
                }

                String hash = cache.lookup(file, relative);
                if (hash == null) {
                    hash = RawMca.hashFile(file);
                }
                repo.getBlobStore().storeWithHash(file, hash);
                cache.remember(file, relative, hash);
                manifest.addWholeFile(relative, null, hash);
                used += size;
                stored++;
            }
        }

        if (stored > 0 || skipped > 0) {
            SaveGitMod.LOGGER.info("Beta 世界文件快照：保存 {} 个，跳过 {} 个，共 {} 字节",
                    stored, skipped, used);
        }
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
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
