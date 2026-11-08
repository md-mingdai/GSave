package com.mingdai.savegit.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mingdai.savegit.SaveGitMod;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置持久化到 config/savegit.json，由 MaLiLib 的 ConfigManager 负责加载与保存。
 */
public final class SaveGitConfigs implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = SaveGitMod.MOD_ID + ".json";
    private static final String GENERIC_KEY = SaveGitMod.MOD_ID + ".config.generic";
    private static final String HOTKEYS_KEY = SaveGitMod.MOD_ID + ".config.hotkeys";

    public static final class Generic {
        public static final ConfigBoolean ENABLED =
                new ConfigBoolean("enabled", true).apply(GENERIC_KEY);
        public static final ConfigInteger AUTO_SNAPSHOT_INTERVAL_MINUTES =
                new ConfigInteger("autoSnapshotIntervalMinutes", 15, 1, 1440, true).apply(GENERIC_KEY);
        public static final ConfigInteger MAX_SNAPSHOTS =
                new ConfigInteger("maxSnapshots", 50, 1, 1000, true).apply(GENERIC_KEY);
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
                AUTO_SNAPSHOT_INTERVAL_MINUTES,
                MAX_SNAPSHOTS,
                SNAPSHOT_REGION,
                SNAPSHOT_ENTITIES,
                SNAPSHOT_POI,
                SNAPSHOT_LEVEL_DAT,
                SNAPSHOT_PLAYERDATA
        );
    }

    public static final class Hotkeys {
        public static final ConfigHotkey OPEN_MAIN_MENU =
                new ConfigHotkey("openMainMenu", "H").apply(HOTKEYS_KEY);

        public static final ImmutableList<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
                OPEN_MAIN_MENU
        );
    }

    public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.<IConfigBase>builder()
            .addAll(Generic.OPTIONS)
            .addAll(Hotkeys.HOTKEY_LIST)
            .build();

    public static boolean isEnabled() {
        return Generic.ENABLED.getBooleanValue();
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

    public static int maxSnapshots() {
        return Generic.MAX_SNAPSHOTS.getIntegerValue();
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

        if (Files.exists(configFile) && Files.isReadable(configFile)) {
            JsonElement element = JsonUtils.parseJsonFileAsPath(configFile);

            if (element != null && element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();
                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
            } else {
                SaveGitMod.LOGGER.error("无法读取配置文件: {}", configFile.toAbsolutePath());
            }
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
        ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
        JsonUtils.writeJsonToFileAsPath(root, dir.resolve(CONFIG_FILE_NAME));
    }
}
