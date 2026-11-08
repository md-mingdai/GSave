package com.mingdai.savegit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 存档内的 savegit 仓库：HEAD、commits、manifests、blobs、patches。
 */
public final class SnapshotRepository {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path worldRoot;
    private final Path saveGitDir;
    private final Path commitsDir;
    private final Path manifestsDir;
    private final Path patchesDir;
    private final Path headFile;
    private final BlobStore blobStore;

    public SnapshotRepository(Path worldRoot) {
        this.worldRoot = worldRoot.toAbsolutePath().normalize();
        this.saveGitDir = this.worldRoot.resolve("savegit");
        this.commitsDir = saveGitDir.resolve("commits");
        this.manifestsDir = saveGitDir.resolve("manifests");
        this.patchesDir = saveGitDir.resolve("patches");
        this.headFile = saveGitDir.resolve("HEAD");
        this.blobStore = new BlobStore(saveGitDir);
    }

    public Path getWorldRoot() {
        return worldRoot;
    }

    public Path getSaveGitDir() {
        return saveGitDir;
    }

    public Path getShotsDir() {
        return saveGitDir.resolve("shots");
    }

    public BlobStore getBlobStore() {
        return blobStore;
    }

    public String readHead() {
        if (!Files.isRegularFile(headFile)) {
            return null;
        }
        try {
            String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();
            return head.isEmpty() ? null : head;
        } catch (IOException e) {
            return null;
        }
    }

    public void writeHead(String commitId) throws IOException {
        writeStringAtomic(headFile, commitId + "\n");
    }

    public Commit getCommit(String id) {
        if (id == null) {
            return null;
        }
        Path path = commitsDir.resolve(id + ".json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Commit.fromJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public void saveCommit(Commit commit) throws IOException {
        writeStringAtomic(commitsDir.resolve(commit.id + ".json"), commit.toJson());
    }

    public Manifest getManifest(String id) {
        if (id == null) {
            return null;
        }
        Path path = manifestsDir.resolve(id + ".json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Manifest.fromJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public String saveManifest(Manifest manifest) throws IOException {
        String manifestId = manifest.computeHash();
        Path path = manifestsDir.resolve(manifestId + ".json");
        if (!Files.isRegularFile(path)) {
            writeStringAtomic(path, manifest.toJson());
        }
        return manifestId;
    }

    public void savePatch(Patch patch) throws IOException {
        writeStringAtomic(patchesDir.resolve(patch.commitId + ".json"), GSON.toJson(patch));
    }

    public Patch getPatch(String commitId) {
        if (commitId == null) {
            return null;
        }
        Path path = patchesDir.resolve(commitId + ".json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Patch.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public List<Commit> listCommits() {
        List<Commit> commits = new ArrayList<>();
        if (!Files.isDirectory(commitsDir)) {
            return commits;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(commitsDir, "*.json")) {
            for (Path path : stream) {
                try {
                    Commit commit = Commit.fromJson(Files.readString(path, StandardCharsets.UTF_8));
                    if (commit != null) {
                        commits.add(commit);
                    }
                } catch (IOException | RuntimeException ignored) {
                    // 损坏的提交文件不影响其余快照的展示
                }
            }
        } catch (IOException ignored) {
            return commits;
        }
        commits.sort(Comparator.comparingLong((Commit c) -> c.timestamp).reversed()
                .thenComparing(c -> c.id, Comparator.reverseOrder()));
        return commits;
    }

    /**
     * 只保留最新的 maxSnapshots 个快照，返回删除的数量。
     * blob 不做回收：多个提交可能共享同一份数据。
     */
    public int cleanupOldSnapshots(int maxSnapshots) {
        int max = Math.max(1, maxSnapshots);
        List<Commit> commits = listCommits();
        if (commits.size() <= max) {
            return 0;
        }

        String head = readHead();
        Set<String> keptManifests = new HashSet<>();
        List<Commit> kept = new ArrayList<>();
        List<Commit> removed = new ArrayList<>();
        for (Commit commit : commits) {
            if (kept.size() < max || commit.id.equals(head)) {
                kept.add(commit);
                keptManifests.add(commit.manifestId);
            } else {
                removed.add(commit);
            }
        }

        for (Commit commit : removed) {
            deleteQuietly(commitsDir.resolve(commit.id + ".json"));
            deleteQuietly(patchesDir.resolve(commit.id + ".json"));
        }

        cleanupUnreferencedManifests(keptManifests);
        return removed.size();
    }

    /**
     * 删除一个提交，并把它的直接子提交 rebase 到它的父提交上。
     * 返回是否删除成功。blob 与截图文件保留（截图按提交 id 命名，不随删除清理）。
     */
    public boolean deleteCommit(String commitId) throws IOException {
        Commit target = getCommit(commitId);
        if (target == null) {
            return false;
        }

        List<Commit> commits = listCommits();
        Commit child = null;
        for (Commit commit : commits) {
            if (commitId.equals(commit.parent)) {
                child = commit;
                break;
            }
        }

        deleteQuietly(commitsDir.resolve(commitId + ".json"));
        deleteQuietly(patchesDir.resolve(commitId + ".json"));
        // 删除该提交对应的截图（截图按提交 id 命名）
        deleteQuietly(getShotsDir().resolve(commitId + ".png"));

        if (child != null) {
            child.parent = target.parent;
            saveCommit(child);

            Manifest parentManifest = target.parent == null ? null : getManifest(target.parent);
            Manifest childManifest = getManifest(child.manifestId);
            savePatch(PatchGenerator.generate(child.id, target.parent, parentManifest, childManifest));
        }

        if (commitId.equals(readHead())) {
            if (target.parent != null) {
                writeHead(target.parent);
            } else {
                deleteQuietly(headFile);
            }
        }

        Set<String> keptManifests = new HashSet<>();
        for (Commit commit : listCommits()) {
            keptManifests.add(commit.manifestId);
        }
        cleanupUnreferencedManifests(keptManifests);
        return true;
    }

    private void cleanupUnreferencedManifests(Set<String> keptManifests) {
        if (!Files.isDirectory(manifestsDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(manifestsDir, "*.json")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                String id = name.substring(0, name.length() - ".json".length());
                if (!keptManifests.contains(id)) {
                    deleteQuietly(path);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static void writeStringAtomic(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (UnsupportedOperationException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
