package com.mingdai.savegit.snapshot;

import java.util.Locale;
import java.util.Set;

/**
 * Beta 世界文件分组的路径规则：区分数据包目录与其他模组文件，并排除不该纳入快照的路径。
 */
public final class WorldFileGroups {
    public static final String GROUP_DATAPACKS = "datapacks";
    public static final String GROUP_OTHER = "other";
    public static final String DATAPACKS_PREFIX = "datapacks/";

    private static final Set<String> EXCLUDED_NAMES = Set.of(
            "session.lock", "level.dat_old", ".ds_store");

    private WorldFileGroups() {
    }

    public static String normalize(String relativePath) {
        return relativePath == null ? "" : relativePath.replace('\\', '/');
    }

    /** gsave 自身、会话锁、临时文件与系统垃圾文件不纳入快照。 */
    public static boolean isExcluded(String relativePath) {
        String path = normalize(relativePath).toLowerCase(Locale.ROOT);
        if (path.isEmpty()) {
            return true;
        }
        if (path.equals("gsave") || path.startsWith("gsave/")) {
            return true;
        }
        // level.dat 与 playerdata 由各自独立的开关处理，绝不归入其他文件分组
        if (path.equals("level.dat") || path.startsWith("playerdata/")) {
            return true;
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (EXCLUDED_NAMES.contains(name)) {
            return true;
        }
        return name.endsWith(".tmp") || name.endsWith(".part") || name.endsWith(".lock");
    }

    public static boolean isDatapack(String relativePath) {
        return normalize(relativePath).startsWith(DATAPACKS_PREFIX);
    }

    public static String classify(String relativePath) {
        return isDatapack(relativePath) ? GROUP_DATAPACKS : GROUP_OTHER;
    }
}
