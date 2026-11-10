package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.SaveGitMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 仓库格式版本。gsave/VERSION 记录格式号，缺失视为 1。
 * 读取到高于当前支持的版本时，仓库进入只读模式。
 */
public final class RepositoryFormat {
    public static final int CURRENT = 2;

    private static final String FILE_NAME = "VERSION";
    private static final Pattern FORMAT_LINE = Pattern.compile("format\\s*=\\s*(\\d+)");

    private RepositoryFormat() {
    }

    public static Path versionFile(Path saveGitDir) {
        return saveGitDir.resolve(FILE_NAME);
    }

    public static int read(Path saveGitDir) {
        Path file = versionFile(saveGitDir);
        if (!Files.isRegularFile(file)) {
            return 1;
        }
        try {
            Matcher matcher = FORMAT_LINE.matcher(
                    Files.readString(file, StandardCharsets.UTF_8));
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return 1;
    }

    public static boolean isNewerThanSupported(int version) {
        return version > CURRENT;
    }

    /** 仓库已存在时才补写版本文件；新仓库由第一次写入时创建。 */
    public static int ensureIfExists(Path saveGitDir) {
        int version = read(saveGitDir);
        if (version < CURRENT && Files.isDirectory(saveGitDir)) {
            write(saveGitDir, CURRENT);
            return CURRENT;
        }
        return version;
    }

    /** 写入路径调用：确保目录与版本文件为当前格式。 */
    public static int ensureWritable(Path saveGitDir) throws IOException {
        int version = read(saveGitDir);
        if (isNewerThanSupported(version)) {
            throw new IOException("仓库格式版本 " + version
                    + " 高于当前支持的 " + CURRENT + "，请升级 GSave");
        }
        if (version < CURRENT) {
            write(saveGitDir, CURRENT);
            return CURRENT;
        }
        return version;
    }

    public static void write(Path saveGitDir, int version) {
        try {
            Files.createDirectories(saveGitDir);
            Path temp = versionFile(saveGitDir).resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, "format=" + version + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temp, versionFile(saveGitDir),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, versionFile(saveGitDir),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            SaveGitMod.LOGGER.warn("写入仓库版本文件失败", e);
        }
    }
}
