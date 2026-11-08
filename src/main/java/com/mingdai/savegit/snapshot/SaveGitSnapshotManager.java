package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiRestoreProgress;
import com.mingdai.savegit.gui.GuiSnapshotList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.InfoUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 客户端侧快照调度：热键、自动定时、进世界基线、退出快照与恢复流程。
 */
public final class SaveGitSnapshotManager {
    private static final int BASELINE_DELAY_TICKS = 100;

    private static boolean inWorld;
    private static int ticksUntilBaseline = -1;
    private static int ticksUntilAuto = -1;

    private static volatile boolean pendingRestore;
    private static volatile String pendingRestoreCommitId;
    private static volatile Path pendingRestoreWorldDir;
    private static volatile boolean restoreStarted;

    private SaveGitSnapshotManager() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SaveGitSnapshotManager::onClientTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(SaveGitSnapshotManager::onServerStopping);
    }

    public static void manualSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SaveGitConfigs.isEnabled()) {
            InfoUtils.sendVanillaMessage(Text.literal("SaveGit 已关闭"));
            return;
        }
        takeSnapshotAsync(client, "手动快照");
    }

    public static void openSnapshotList() {
        GuiBase.openGui(new GuiSnapshotList());
    }

    public static Path getWorldDirectory(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return null;
        }
        try {
            return server.getSavePath(WorldSavePath.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private static void onClientTick(MinecraftClient client) {
        checkPendingRestore(client);

        IntegratedServer server = client.getServer();
        boolean inSinglePlayer = server != null && client.world != null;

        if (!inSinglePlayer) {
            inWorld = false;
            ticksUntilBaseline = -1;
            ticksUntilAuto = -1;
            return;
        }

        if (!inWorld) {
            inWorld = true;
            ticksUntilBaseline = BASELINE_DELAY_TICKS;
            ticksUntilAuto = SaveGitConfigs.autoSnapshotIntervalTicks();
        }

        if (!SaveGitConfigs.isEnabled()) {
            ticksUntilBaseline = -1;
            ticksUntilAuto = SaveGitConfigs.autoSnapshotIntervalTicks();
            return;
        }

        if (ticksUntilBaseline >= 0) {
            ticksUntilBaseline--;
            if (ticksUntilBaseline <= 0) {
                ticksUntilBaseline = -1;
                takeSnapshotAsync(client, "世界加载基线");
            }
        }

        if (ticksUntilAuto >= 0) {
            ticksUntilAuto--;
            if (ticksUntilAuto <= 0) {
                ticksUntilAuto = SaveGitConfigs.autoSnapshotIntervalTicks();
                takeSnapshotAsync(client, "自动快照");
            }
        }
    }

    private static void takeSnapshotAsync(MinecraftClient client, String message) {
        IntegratedServer server = client.getServer();
        if (server == null || client.world == null) {
            InfoUtils.sendVanillaMessage(Text.literal("当前不在单人世界中"));
            return;
        }

        Path worldDir = getWorldDirectory(client);
        if (worldDir == null) {
            InfoUtils.sendVanillaMessage(Text.literal("无法获取世界存档目录"));
            return;
        }

        String worldName = worldName(worldDir);
        WorldSnapshotter.Settings settings = snapshotSettings(worldName, SaveGitConfigs.maxSnapshots());

        // 在服务端线程先 save() 刷盘，再执行快照，保证数据一致
        server.execute(() -> {
            try {
                server.save(true, true, true);
                SnapshotRepository repo = new SnapshotRepository(worldDir);
                Commit commit = new WorldSnapshotter(repo).takeSnapshot(message, settings);
                notifyAsync(client, "快照已创建: " + commit.shortId() + "（" + message + "）");
            } catch (Throwable t) {
                SaveGitMod.LOGGER.error("创建快照失败", t);
                notifyAsync(client, "快照失败: " + t.getMessage());
            }
        });
    }

    public static void requestRestore(MinecraftClient client, Commit commit) {
        IntegratedServer server = client.getServer();
        if (server == null || client.world == null) {
            InfoUtils.sendVanillaMessage(Text.literal("当前不在单人世界中，无法恢复"));
            return;
        }

        Path worldDir = getWorldDirectory(client);
        if (worldDir == null) {
            InfoUtils.sendVanillaMessage(Text.literal("无法获取世界存档目录"));
            return;
        }

        String worldName = worldName(worldDir);
        // 安全快照不触发旧快照清理，避免清理掉用户正要恢复的目标快照
        WorldSnapshotter.Settings safetySettings = snapshotSettings(worldName, Integer.MAX_VALUE);

        server.execute(() -> {
            try {
                if (new SnapshotRepository(worldDir).getCommit(commit.id) == null) {
                    notifyAsync(client, "恢复失败：找不到所选快照");
                    return;
                }
                server.save(true, true, true);
                new WorldSnapshotter(new SnapshotRepository(worldDir))
                        .takeSnapshot("恢复前自动快照", safetySettings);
            } catch (Throwable t) {
                SaveGitMod.LOGGER.error("创建恢复前安全快照失败", t);
                notifyAsync(client, "恢复已取消：安全快照创建失败（" + t.getMessage() + "）");
                return;
            }

            client.execute(() -> {
                pendingRestore = true;
                restoreStarted = false;
                pendingRestoreCommitId = commit.id;
                pendingRestoreWorldDir = worldDir;
                // 回到标题界面，等待世界与集成服务端完全卸载
                client.disconnectWithSavingScreen();
            });
        });
    }

    private static void checkPendingRestore(MinecraftClient client) {
        if (!pendingRestore) {
            // 确保自定义进度界面不会被原版随后设置的标题界面覆盖
            if (restoreStarted && RestoreProgress.isActive()
                    && client.world == null && client.getServer() == null
                    && !(client.currentScreen instanceof GuiRestoreProgress)) {
                client.setScreen(new GuiRestoreProgress());
            }
            return;
        }

        if (client.world != null || client.getServer() != null) {
            return;
        }

        pendingRestore = false;
        String commitId = pendingRestoreCommitId;
        Path worldDir = pendingRestoreWorldDir;
        pendingRestoreCommitId = null;
        pendingRestoreWorldDir = null;
        restoreStarted = true;

        client.setScreen(new GuiRestoreProgress());

        new Thread(() -> {
            try {
                SnapshotRepository repo = new SnapshotRepository(worldDir);
                Commit commit = repo.getCommit(commitId);
                if (commit == null) {
                    throw new IOException("找不到快照提交: " + commitId);
                }
                new WorldRestorer(repo, worldDir).restore(commit);
            } catch (Throwable t) {
                SaveGitMod.LOGGER.error("恢复快照失败", t);
                RestoreProgress.fail(t.getMessage());
            }
        }, "SaveGit-Restore").start();
    }

    private static void onServerStopping(MinecraftServer server) {
        if (pendingRestore) {
            return;
        }
        if (!SaveGitConfigs.isEnabled()) {
            return;
        }

        try {
            Path worldDir = server.getSavePath(WorldSavePath.ROOT);
            server.save(true, true, true);
            new WorldSnapshotter(new SnapshotRepository(worldDir))
                    .takeSnapshot("世界断开", snapshotSettings(worldName(worldDir), SaveGitConfigs.maxSnapshots()));
        } catch (Throwable t) {
            SaveGitMod.LOGGER.error("创建退出快照失败", t);
        }
    }

    private static WorldSnapshotter.Settings snapshotSettings(String worldName, int maxSnapshots) {
        return new WorldSnapshotter.Settings(
                SaveGitConfigs.snapshotRegion(),
                SaveGitConfigs.snapshotEntities(),
                SaveGitConfigs.snapshotPoi(),
                SaveGitConfigs.snapshotLevelDat(),
                SaveGitConfigs.snapshotPlayerdata(),
                maxSnapshots,
                worldName,
                SaveGitMod.MC_VERSION
        );
    }

    private static String worldName(Path worldDir) {
        Path name = worldDir.getFileName();
        return name == null ? "未知世界" : name.toString();
    }

    private static void notifyAsync(MinecraftClient client, String message) {
        client.execute(() -> InfoUtils.sendVanillaMessage(Text.literal(message)));
    }
}
