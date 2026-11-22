package com.mingdai.savegit.preview;

import com.mingdai.savegit.snapshot.ChunkPreviewMap;
import com.mingdai.savegit.snapshot.ChunkSelection;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.Manifest;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.snapshot.WorldRestorer;

import java.nio.file.Path;

/**
 * 一次恢复预览的计算结果：后台比较目标快照与当前世界，得到区块状态。
 */
public final class PreviewSession {
    public enum Status {
        COMPUTING,
        READY,
        FAILED
    }

    private final Commit commit;
    private final Path worldDir;
    private final WorldRestorer.RestoreFilter filter;
    private final ChunkSelection selection;
    private final boolean deleteNewFiles;

    private volatile Status status = Status.COMPUTING;
    private volatile ChunkPreviewMap previewMap;
    private volatile String error;

    public PreviewSession(Commit commit, Path worldDir, WorldRestorer.RestoreFilter filter,
                          ChunkSelection selection, boolean deleteNewFiles) {
        this.commit = commit;
        this.worldDir = worldDir;
        this.filter = filter;
        this.selection = selection;
        this.deleteNewFiles = deleteNewFiles;
    }

    public void computeAsync() {
        Thread thread = new Thread(this::compute, "GSave-Preview");
        thread.setDaemon(true);
        thread.start();
    }

    private void compute() {
        try {
            SnapshotRepository repo = new SnapshotRepository(this.worldDir);
            Manifest manifest = repo.getManifest(this.commit.manifestId);
            if (manifest == null) {
                this.error = "manifest missing";
                this.status = Status.FAILED;
                return;
            }
            this.previewMap = ChunkPreviewMap.compute(this.worldDir, manifest,
                    this.filter, this.selection);
            this.status = Status.READY;
        } catch (Throwable t) {
            this.error = t.getMessage() == null ? t.toString() : t.getMessage();
            this.status = Status.FAILED;
        }
    }

    public Status status() {
        return this.status;
    }

    public ChunkPreviewMap previewMap() {
        return this.previewMap;
    }

    public String error() {
        return this.error;
    }

    public Commit commit() {
        return this.commit;
    }

    public WorldRestorer.RestoreFilter filter() {
        return this.filter;
    }

    public ChunkSelection selection() {
        return this.selection;
    }

    public boolean deleteNewFiles() {
        return this.deleteNewFiles;
    }
}
