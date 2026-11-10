package com.mingdai.savegit.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mingdai.savegit.SaveGitMod;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置持久化到 config/gsave.json，由 MaLiLib 的 ConfigManager 负责加载与保存。
 */
public final class SaveGitConfigs implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = SaveGitMod.MOD_ID + ".json";
    private static final String LEGACY_CONFIG_FILE_NAME = "savegit.json";
    private static final String GENERIC_KEY = SaveGitMod.MOD_ID + ".config.generic";

    public static final class Generic {
        public static final ConfigBoolean ENABLED =
                new ConfigBoolean("enabled", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SCHEDULED_SNAPSHOT_ENABLED =
                new ConfigBoolean("scheduledSnapshotEnabled", false).apply(GENERIC_KEY);
        public static final ConfigInteger AUTO_SNAPSHOT_INTERVAL_MINUTES =
                new ConfigInteger("autoSnapshotIntervalMinutes", 15, 1, 1440, true).apply(GENERIC_KEY);
        public static final ConfigInteger SCHEDULED_SNAPSHOT_STOP_COUNT =
                new ConfigInteger("scheduledSnapshotStopCount", 0, 0, 100000, false).apply(GENERIC_KEY);
        public static final ConfigBoolean BASELINE_SNAPSHOT_ENABLED =
                new ConfigBoolean("baselineSnapshotEnabled", true).apply(GENERIC_KEY);
        public static final ConfigBoolean DISCONNECT_SNAPSHOT_ENABLED =
                new ConfigBoolean("disconnectSnapshotEnabled", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SKIP_UNCHANGED_AUTOMATIC =
                new ConfigBoolean("skipUnchangedAutomatic", true).apply(GENERIC_KEY);
        public static final ConfigBoolean PROTECT_MANUAL_SNAPSHOTS =
                new ConfigBoolean("protectManualSnapshots", true).apply(GENERIC_KEY);
        public static final ConfigBoolean UNLIMITED_SNAPSHOTS =
                new ConfigBoolean("unlimitedSnapshots", false).apply(GENERIC_KEY);
        public static final ConfigInteger MAX_SNAPSHOTS =
                new ConfigInteger("maxSnapshots", 50, 1, 1000, true).apply(GENERIC_KEY);
        public static final ConfigOptionList SNAPSHOT_PRIORITY =
                new ConfigOptionList("snapshotPriority", SnapshotPriority.GAMEPLAY).apply(GENERIC_KEY);
        public static final ConfigBoolean SNAPSHOT_REGION =
                new ConfigBoolean("snapshotRegion", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SNAPSHOT_ENTITIES =
                new ConfigBoolean("snapshotEntities", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SNAPSHOT_POI =
                new ConfigBoolean("snapshotPoi", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SNAPSHOT_LEVEL_DAT =
                new ConfigBoolean("snapshotLevelDat", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SNAPSHOT_PLAYERDATA =
                new ConfigBoolean("snapshotPlayerdata", true).apply(GENERIC_KEY);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                ENABLED,
                SCHEDULED_SNAPSHOT_ENABLED,
                AUTO_SNAPSHOT_INTERVAL_MINUTES,
                SCHEDULED_SNAPSHOT_STOP_COUNT,
                BASELINE_SNAPSHOT_ENABLED,
                DISCONNECT_SNAPSHOT_ENABLED,
                SKIP_UNCHANGED_AUTOMATIC,
                PROTECT_MANUAL_SNAPSHOTS,
                UNLIMITED_SNAPSHOTS,
                MAX_SNAPSHOTS,
                SNAPSHOT_PRIORITY,
                SNAPSHOT_REGION,
                SNAPSHOT_ENTITIES,
                SNAPSHOT_POI,
                SNAPSHOT_LEVEL_DAT,
                SNAPSHOT_PLAYERDATA
        );
    }

    public static final ImmutableList<IConfigBase> OPTIONS = Generic.OPTIONS;

    public static boolean isEnabled() {
        return Generic.ENABLED.getBooleanValue();
    }

    public static boolean isScheduledSnapshotEnabled() {
        return Generic.SCHEDULED_SNAPSHOT_ENABLED.getBooleanValue();
    }

    public static boolean isBaselineSnapshotEnabled() {
        return Generic.BASELINE_SNAPSHOT_ENABLED.getBooleanValue();
    }

    public static boolean isDisconnectSnapshotEnabled() {
        return Generic.DISCONNECT_SNAPSHOT_ENABLED.getBooleanValue();
    }

    public static boolean skipUnchangedAutomatic() {
        return Generic.SKIP_UNCHANGED_AUTOMATIC.getBooleanValue();
    }

    public static boolean protectManualSnapshots() {
        return Generic.PROTECT_MANUAL_SNAPSHOTS.getBooleanValue();
    }

    public static boolean snapshotRegion() {
        return Generic.SNAPSHOT_REGION.getBooleanValue();
    }

    public static boolean snapshotEntities() {
        return Generic.SNAPSHOT_ENTITIES.getBooleanValue();
    }

    public static boolean snapshotPoi() {
        return Generic.SNAPSHOT_POI.getBooleanValue();
    }

    public static boolean snapshotLevelDat() {
        return Generic.SNAPSHOT_LEVEL_DAT.getBooleanValue();
    }

    public static boolean snapshotPlayerdata() {
        return Generic.SNAPSHOT_PLAYERDATA.getBooleanValue();
    }

    public static int autoSnapshotIntervalTicks() {
        return autoSnapshotIntervalMinutes() * 20 * 60;
    }

    public static int autoSnapshotIntervalMinutes() {
        return Generic.AUTO_SNAPSHOT_INTERVAL_MINUTES.getIntegerValue();
    }

    public static int scheduledSnapshotStopCount() {
        return Generic.SCHEDULED_SNAPSHOT_STOP_COUNT.getIntegerValue();
    }

    public static int maxSnapshots() {
        return isUnlimitedSnapshots() ? Integer.MAX_VALUE : Generic.MAX_SNAPSHOTS.getIntegerValue();
    }

    public static boolean isUnlimitedSnapshots() {
        return Generic.UNLIMITED_SNAPSHOTS.getBooleanValue();
    }

    public static SnapshotPriority snapshotPriority() {
        return (SnapshotPriority) Generic.SNAPSHOT_PRIORITY.getOptionListValue();
    }

    public static boolean isGameplayPriority() {
        return snapshotPriority() == SnapshotPriority.GAMEPLAY;
    }

    @Override
    public void load() {
        loadFromFile();
    }

    @Override
    public void save() {
        saveToFile();
    }

    public static void loadFromFile() {
        Path configFile = FileUtils.getConfigDirectoryAsPath().resolve(CONFIG_FILE_NAME);
        migrateLegacyConfig(configFile);

        if (Files.exists(configFile) && Files.isReadable(configFile)) {
            JsonElement element = JsonUtils.parseJsonFileAsPath(configFile);

            if (element != null && element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();
                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
            } else {
                SaveGitMod.LOGGER.error("无法读取配置文件: {}", configFile.toAbsolutePath());
            }
        }
    }

    private static void migrateLegacyConfig(Path configFile) {
        Path legacy = configFile.resolveSibling(LEGACY_CONFIG_FILE_NAME);

        if (Files.exists(configFile) || !Files.exists(legacy)) {
            return;
        }

        try {
            Files.move(legacy, configFile);
            SaveGitMod.LOGGER.info("已迁移旧配置文件: {} -> {}", legacy, configFile);
        } catch (IOException e) {
            SaveGitMod.LOGGER.warn("迁移旧配置文件失败: {}", legacy, e);
        }
    }

    public static void saveToFile() {
        Path dir = FileUtils.getConfigDirectoryAsPath();

        if (!Files.exists(dir)) {
            FileUtils.createDirectoriesIfMissing(dir);
        }
        if (!Files.isDirectory(dir)) {
            SaveGitMod.LOGGER.error("配置目录不存在: {}", dir.toAbsolutePath());
            return;
        }

        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
        JsonUtils.writeJsonToFileAsPath(root, dir.resolve(CONFIG_FILE_NAME));
    }
}
