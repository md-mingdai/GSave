package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.util.L10n;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 仓库自检：检查提交链、清单、blob 与补丁的完整性，并给出可修复/可清理项。
 */
public final class RepositoryChecker {
    public enum Level {
        ERROR,
        WARNING,
        INFO
    }

    public record Issue(Level level, String key, Object... args) {
    }

    public record Report(int formatVersion, boolean readOnly, int commitCount, int manifestCount,
                         int blobCount, long packBytes, List<Issue> issues) {
        public long count(Level level) {
            return this.issues.stream().filter(issue -> issue.level() == level).count();
        }
    }

    private RepositoryChecker() {
    }

    public static Report check(Path worldRoot, BiConsumer<Integer, String> progress)
            throws IOException {
        SnapshotRepository repo = new SnapshotRepository(worldRoot);
        List<Issue> issues = new ArrayList<>();
        BlobStore store = repo.getBlobStore();

        progress.accept(5, L10n.tr("gsave.check.log.open"));
        List<Commit> commits = repo.listCommits();
        Map<String, Commit> byId = new HashMap<>();
        Set<String> commitIds = new HashSet<>();
        Set<String> manifestIds = new HashSet<>();
        for (Commit commit : commits) {
            byId.put(commit.id, commit);
            commitIds.add(commit.id);
            manifestIds.add(commit.manifestId);
        }

        if (repo.isReadOnly()) {
            issues.add(new Issue(Level.WARNING, "gsave.check.repo_too_new",
                    String.valueOf(repo.getFormatVersion())));
        }

        // HEAD 与线性链
        String head = repo.readHead();
        if (head == null) {
            if (!commits.isEmpty()) {
                issues.add(new Issue(Level.WARNING, "gsave.check.head_missing"));
            }
        } else if (!byId.containsKey(head)) {
            issues.add(new Issue(Level.ERROR, "gsave.check.head_dangling", head));
        } else {
            Set<String> visited = new HashSet<>();
            String cursor = head;
            boolean broken = false;
            while (cursor != null) {
                if (!visited.add(cursor)) {
                    issues.add(new Issue(Level.ERROR, "gsave.check.chain_cycle", cursor));
                    broken = true;
                    break;
                }
                Commit commit = byId.get(cursor);
                if (commit == null) {
                    issues.add(new Issue(Level.ERROR, "gsave.check.chain_broken", cursor));
                    broken = true;
                    break;
                }
                cursor = commit.parent;
            }
            if (!broken) {
                int unreachable = commits.size() - visited.size();
                if (unreachable > 0) {
                    issues.add(new Issue(Level.WARNING, "gsave.check.unreachable",
                            String.valueOf(unreachable)));
                }
            }
        }

        progress.accept(20, L10n.tr("gsave.check.log.commits",
                String.valueOf(commits.size())));

        // 清单与补丁
        int checked = 0;
        for (Commit commit : commits) {
            if (commit.manifestId == null || repo.getManifest(commit.manifestId) == null) {
                issues.add(new Issue(Level.ERROR, "gsave.check.manifest_missing",
                        commit.shortId()));
            }
            if (commit.parent != null && repo.getPatch(commit.id) == null) {
                issues.add(new Issue(Level.WARNING, "gsave.check.patch_missing",
                        commit.shortId()));
            }
            if (commit.timestamp <= 0) {
                issues.add(new Issue(Level.WARNING, "gsave.check.bad_timestamp",
                        commit.shortId()));
            }
            checked++;
            if (commits.size() > 0 && checked % 50 == 0) {
                progress.accept(20 + 30 * checked / commits.size(),
                        L10n.tr("gsave.check.log.commits", String.valueOf(checked)));
            }
        }

        // blob 完整性
        Map<String, Manifest> manifests = new HashMap<>();
        Set<String> blobHashes = new HashSet<>();
        Set<String> missing = new HashSet<>();
        for (Commit commit : commits) {
            Manifest manifest = manifests.computeIfAbsent(commit.manifestId, repo::getManifest);
            if (manifest == null) {
                continue;
            }
            for (Manifest.FileEntry entry : manifest.getAllEntries()) {
                if (entry.isWhole() && entry.hash != null) {
                    blobHashes.add(entry.hash);
                } else if (entry.isMca()) {
                    for (String hash : entry.chunks.values()) {
                        if (hash != null) {
                            blobHashes.add(hash);
                        }
                    }
                }
            }
        }
        progress.accept(60, L10n.tr("gsave.check.log.blobs",
                String.valueOf(blobHashes.size())));
        int blobChecked = 0;
        for (String hash : blobHashes) {
            if (!store.exists(hash)) {
                missing.add(hash);
                if (missing.size() <= 5) {
                    issues.add(new Issue(Level.ERROR, "gsave.check.blob_missing", hash));
                }
            }
            blobChecked++;
            if (blobHashes.size() > 0 && blobChecked % 500 == 0) {
                progress.accept(60 + 30 * blobChecked / blobHashes.size(),
                        L10n.tr("gsave.check.log.blobs", String.valueOf(blobChecked)));
            }
        }
        if (missing.size() > 5) {
            issues.add(new Issue(Level.ERROR, "gsave.check.blob_missing_more",
                    String.valueOf(missing.size() - 5)));
        }

        // 孤立文件
        long orphanShots = RepositoryHousekeeping.countOrphanShots(
                repo.getSaveGitDir(), commitIds);
        if (orphanShots > 0) {
            issues.add(new Issue(Level.INFO, "gsave.check.orphan_shots",
                    String.valueOf(orphanShots)));
        }
        long orphanPatches = RepositoryHousekeeping.countOrphanPatches(
                repo.getSaveGitDir(), commitIds);
        if (orphanPatches > 0) {
            issues.add(new Issue(Level.INFO, "gsave.check.orphan_patches",
                    String.valueOf(orphanPatches)));
        }
        long orphanManifests = RepositoryHousekeeping.countOrphanManifests(
                repo.getSaveGitDir(), manifestIds);
        if (orphanManifests > 0) {
            issues.add(new Issue(Level.INFO, "gsave.check.orphan_manifests",
                    String.valueOf(orphanManifests)));
        }
        long temps = RepositoryHousekeeping.countTempFiles(repo.getSaveGitDir());
        if (temps > 0) {
            issues.add(new Issue(Level.INFO, "gsave.check.temp_files",
                    String.valueOf(temps)));
        }
        if (!Files.isDirectory(repo.getSaveGitDir().resolve("packs"))
                && !commits.isEmpty()) {
            issues.add(new Issue(Level.ERROR, "gsave.check.packs_missing"));
        }

        progress.accept(95, L10n.tr("gsave.check.log.done"));
        store.close();

        return new Report(repo.getFormatVersion(), repo.isReadOnly(), commits.size(),
                manifests.size(), blobHashes.size(), store.totalPackedBytes(), issues);
    }

    /** 只做安全修复：补写 HEAD、重建缺失补丁、清理临时文件与无主文件。 */
    public static int repair(Path worldRoot) throws IOException {
        SnapshotRepository repo = new SnapshotRepository(worldRoot);
        repo.ensureWritableFormat();
        int repaired = 0;
        List<Commit> commits = repo.listCommits();

        String head = repo.readHead();
        if (head == null && !commits.isEmpty()) {
            repo.writeHead(commits.get(0).id);
            repaired++;
        }

        for (Commit commit : commits) {
            if (commit.parent == null || repo.getPatch(commit.id) != null) {
                continue;
            }
            Manifest manifest = repo.getManifest(commit.manifestId);
            if (manifest == null) {
                continue;
            }
            Commit parent = repo.getCommit(commit.parent);
            Manifest parentManifest = parent == null ? null : repo.getManifest(parent.manifestId);
            repo.savePatch(PatchGenerator.generate(commit.id, commit.parent,
                    parentManifest, manifest));
            repaired++;
        }

        Set<String> commitIds = new HashSet<>();
        Set<String> manifestIds = new HashSet<>();
        for (Commit commit : commits) {
            commitIds.add(commit.id);
            manifestIds.add(commit.manifestId);
        }
        repaired += RepositoryHousekeeping.deleteOrphanShots(
                repo.getSaveGitDir(), commitIds);
        repaired += RepositoryHousekeeping.deleteOrphanPatches(
                repo.getSaveGitDir(), commitIds);
        repaired += RepositoryHousekeeping.deleteOrphanManifests(
                repo.getSaveGitDir(), manifestIds);
        repaired += RepositoryHousekeeping.deleteTempFiles(repo.getSaveGitDir());
        return repaired;
    }

    static long directorySize(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    static long countFiles(Path dir, String glob) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        long count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path ignored : stream) {
                count++;
            }
        } catch (IOException ignored) {
        }
        return count;
    }
}
