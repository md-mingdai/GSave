package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiRestoreProgress;
import com.mingdai.savegit.gui.GuiSaveGitMain;
import com.mingdai.savegit.input.SaveGitKeyBindings;
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
    private static volatile IntegratedServer pendingTeardownServer;
    private static int teardownTicks;

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
        handleMainMenuKey(client);
        checkPendingRestore(client);

        IntegratedServer server = client.getServer();
        boolean inSinglePlayer = server != null && client.world != null;

        if (!inSinglePlayer) {
            inWorld = false;
            ticksUntilBaseline = -1;
            ticksUntilAuto = -1;
            SnapshotStatus.reset();
            return;
        }

        if (!inWorld) {
            inWorld = true;
            ticksUntilBaseline = SaveGitConfigs.isBaselineSnapshotEnabled() ? BASELINE_DELAY_TICKS : -1;
            ticksUntilAuto = SaveGitConfigs.isScheduledSnapshotEnabled()
                    ? SaveGitConfigs.autoSnapshotIntervalTicks() : -1;
        }

        if (!SaveGitConfigs.isEnabled()) {
            ticksUntilBaseline = -1;
            ticksUntilAuto = -1;
            return;
        }

        if (!SaveGitConfigs.isBaselineSnapshotEnabled()) {
            ticksUntilBaseline = -1;
        }

        if (!SaveGitConfigs.isScheduledSnapshotEnabled()) {
            ticksUntilAuto = -1;
        } else if (ticksUntilAuto < 0) {
            ticksUntilAuto = SaveGitConfigs.autoSnapshotIntervalTicks();
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
                takeSnapshotAsync(client, "定时快照");
            }
        }
    }

    private static void handleMainMenuKey(MinecraftClient client) {
        while (SaveGitKeyBindings.OPEN_MAIN_MENU.wasPressed()) {
            if (client.currentScreen == null) {
                GuiBase.openGui(new GuiSaveGitMain());
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
        SnapshotStatus.begin(message);
        try {
            server.execute(() -> {
                try {
                    server.save(true, true, true);
                    SnapshotRepository repo = new SnapshotRepository(worldDir);
                Commit commit = new WorldSnapshotter(repo).takeSnapshot(message, settings);
                notifyAsync(client, "快照已创建: " + commit.shortId() + "（" + message + "）");
                } catch (Throwable t) {
                    SaveGitMod.LOGGER.error("创建快照失败", t);
                    notifyAsync(client, "快照失败: " + t.getMessage());
                } finally {
                    SnapshotStatus.end();
                }
            });
        } catch (Throwable t) {
            SnapshotStatus.end();
            SaveGitMod.LOGGER.error("调度快照任务失败", t);
            notifyAsync(client, "快照失败: " + t.getMessage());
        }
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

        SnapshotStatus.begin("恢复前自动快照");
        try {
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
            } finally {
                SnapshotStatus.end();
            }

            client.execute(() -> {
                pendingRestore = true;
                restoreStarted = false;
                pendingRestoreCommitId = commit.id;
                pendingRestoreWorldDir = worldDir;
                pendingTeardownServer = server;
                teardownTicks = 0;

                // 原版 disconnectWithSavingScreen() 会一直等待 isShutdown()。
                // 某些情况下服务端收不到 singleplayer owner 的登出事件，
                // 会导致“保存世界中”永久卡住，所以这里主动停止集成服务端。
                if (!server.isStopping()) {
                    SaveGitMod.LOGGER.info("停止集成服务端以开始恢复快照");
                    server.stop(false);
                }
                client.disconnectWithSavingScreen();
                });
            });
        } catch (Throwable t) {
            SnapshotStatus.end();
            SaveGitMod.LOGGER.error("调度恢复流程失败", t);
            notifyAsync(client, "恢复已取消:" + t.getMessage());
        }
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

        // 看门狗：服务端若迟迟不停止，再次强制 halt，避免永久停在“保存世界中”
        teardownTicks++;
        IntegratedServer teardownServer = pendingTeardownServer;
        if (teardownServer != null && !teardownServer.isStopping()) {
            if (teardownTicks == 200) {
                SaveGitMod.LOGGER.warn("等待集成服务端停止超时，再次强制停止");
                teardownServer.stop(false);
            } else if (teardownTicks == 600) {
                SaveGitMod.LOGGER.error("集成服务端仍未停止，恢复流程可能卡住");
            }
        }

        if (client.world != null || client.getServer() != null) {
            return;
        }

        pendingRestore = false;
        pendingTeardownServer = null;
        teardownTicks = 0;
        String commitId = pendingRestoreCommitId;
        Path worldDir = pendingRestoreWorldDir;
        pendingRestoreCommitId = null;
        pendingRestoreWorldDir = null;
        restoreStarted = true;

        SaveGitMod.LOGGER.info("世界已卸载，开始恢复快照 {}", commitId);
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

        SnapshotStatus.begin("世界断开");
        try {
            Path worldDir = server.getSavePath(WorldSavePath.ROOT);
            server.save(true, true, true);
            new WorldSnapshotter(new SnapshotRepository(worldDir))
                    .takeSnapshot("世界断开", snapshotSettings(worldName(worldDir), SaveGitConfigs.maxSnapshots()));
        } catch (Throwable t) {
            SaveGitMod.LOGGER.error("创建退出快照失败", t);
        } finally {
            SnapshotStatus.end();
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
