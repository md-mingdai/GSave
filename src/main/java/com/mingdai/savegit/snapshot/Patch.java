package com.mingdai.savegit.snapshot;

import java.util.Map;
import java.util.TreeMap;

/**
 * 两个 manifest 之间的区块级差异。
 */
public final class Patch {
    public String commitId;
    public String parentCommitId;
    public final Map<String, FileChange> files = new TreeMap<>();

    public enum ChangeType {
        ADDED,
        MODIFIED,
        REMOVED
    }

    public static final class FileChange {
        public ChangeType change;
        public String type;      // mca / whole
        public String oldHash;   // 整文件旧哈希
        public String newHash;   // 整文件新哈希
        public final Map<Integer, ChunkChange> chunks = new TreeMap<>();
    }

    public static final class ChunkChange {
        public ChangeType type;
        public String blobHash;

        public ChunkChange() {
        }

        public ChunkChange(ChangeType type, String blobHash) {
            this.type = type;
            this.blobHash = blobHash;
        }
    }
}
