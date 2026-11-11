package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.util.L10n;
import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiRestoreProgress;
import com.mingdai.savegit.gui.GuiSaveGitMain;
import com.mingdai.savegit.input.SaveGitKeyBindings;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.InfoUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端侧快照调度：热键、定时、进世界基线、退出快照与恢复流程。
 */
public final class SaveGitSnapshotManager {
    private static final int BASELINE_DELAY_TICKS = 100;
    private static final int CLEAN_SHOT_DELAY_TICKS = 2;
    private static final AtomicLong SHOT_COUNTER = new AtomicLong();

    private record PendingShot(Path temp, Runnable afterCapture) {
    }

    private static final java.util.ArrayDeque<PendingShot> PENDING_SHOTS = new java.util.ArrayDeque<>();
    private static int pendingShotDelay;
    private static Screen pendingShotRestoreScreen;

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
            InfoUtils.sendVanillaMessage(L10n.text("gsave.message.disabled"));
            return;
        }
        takeSnapshotAsync(client, L10n.REASON_MANUAL);
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

    public static String getWorldName(MinecraftClient client) {
        return worldName(client.getServer(), getWorldDirectory(client));
    }

    private static void onClientTick(MinecraftClient client) {
        processPendingShots(client);
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
                takeSnapshotAsync(client, L10n.REASON_BASELINE);
            }
        }

        if (ticksUntilAuto >= 0) {
            ticksUntilAuto--;
            if (ticksUntilAuto <= 0) {
                ticksUntilAuto = SaveGitConfigs.autoSnapshotIntervalTicks();
                if (shouldStopScheduledSnapshots(client)) {
                    SaveGitConfigs.Generic.SCHEDULED_SNAPSHOT_ENABLED.setBooleanValue(false);
                    SaveGitConfigs.saveToFile();
                    ticksUntilAuto = -1;
                    InfoUtils.sendVanillaMessage(L10n.text("gsave.message.threshold_disabled"));
                } else {
                    takeSnapshotAsync(client, L10n.REASON_SCHEDULED);
                }
            }
        }
    }

    private static boolean shouldStopScheduledSnapshots(MinecraftClient client) {
        int limit = SaveGitConfigs.scheduledSnapshotStopCount();
        if (limit <= 0) {
            return false;
        }
        Path worldDir = getWorldDirectory(client);
        if (worldDir == null) {
            return false;
        }
        return new SnapshotRepository(worldDir).listCommits().size() >= limit;
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
            InfoUtils.sendVanillaMessage(L10n.text("gsave.message.no_world"));
            return;
        }

        Path worldDir = getWorldDirectory(client);
        if (worldDir == null) {
            InfoUtils.sendVanillaMessage(L10n.text("gsave.message.no_world_dir"));
            return;
        }

        String worldName = worldName(server, worldDir);
        boolean skipUnchanged = SaveGitConfigs.skipUnchangedAutomatic()
                && !L10n.REASON_MANUAL.equals(message);
        WorldSnapshotter.Settings settings =
                snapshotSettings(worldName, SaveGitConfigs.maxSnapshots(), skipUnchanged);
        Path shotTemp = newTempShotPath(worldDir);

        captureScreenshot(client, shotTemp,
                () -> startWorldSnapshot(client, server, worldDir, message, settings, shotTemp));
    }

    private static void startWorldSnapshot(MinecraftClient client, IntegratedServer server, Path worldDir,
                                           String message, WorldSnapshotter.Settings settings, Path shotTemp) {
        SnapshotStatus.begin(message);

        if (SaveGitConfigs.isGameplayPriority()) {
            // 游戏体验优先：服务端线程只负责 save()，耗时的哈希/写盘放到后台线程
            try {
                server.execute(() -> {
                    try {
                        server.save(true, true, true);
                    } catch (Throwable t) {
                        SnapshotStatus.end();
                        deleteQuietly(shotTemp);
                        notifyAsync(client, L10n.tr("gsave.message.snapshot_failed", t.getMessage()));
                        return;
                    }
                    startBackgroundSnapshot(client, worldDir, message, settings, shotTemp);
                });
            } catch (Throwable t) {
                SnapshotStatus.end();
                deleteQuietly(shotTemp);
                notifyAsync(client, L10n.tr("gsave.message.snapshot_failed", t.getMessage()));
            }
        } else {
            try {
                server.execute(() -> {
                    try {
                        server.save(true, true, true);
                        runSnapshot(client, worldDir, message, settings, shotTemp);
                    } catch (Throwable t) {
                        SaveGitMod.LOGGER.error("创建快照失败", t);
                        deleteQuietly(shotTemp);
                        notifyAsync(client, L10n.tr("gsave.message.snapshot_failed", t.getMessage()));
                    } finally {
                        SnapshotStatus.end();
                    }
                });
            } catch (Throwable t) {
                SnapshotStatus.end();
                deleteQuietly(shotTemp);
                notifyAsync(client, L10n.tr("gsave.message.snapshot_failed", t.getMessage()));
            }
        }
    }

    private static void startBackgroundSnapshot(MinecraftClient client, Path worldDir, String message,
                                                WorldSnapshotter.Settings settings, Path shotTemp) {
        Thread thread = new Thread(() -> {
            try {
                runSnapshot(client, worldDir, message, settings, shotTemp);
            } catch (Throwable t) {
                SaveGitMod.LOGGER.error("创建快照失败", t);
                deleteQuietly(shotTemp);
                notifyAsync(client, L10n.tr("gsave.message.snapshot_failed", t.getMessage()));
            } finally {
                SnapshotStatus.end();
            }
        }, "GSave-Snapshot");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runSnapshot(MinecraftClient client, Path worldDir, String message,
                                    WorldSnapshotter.Settings settings, Path shotTemp) throws IOException {
        SnapshotRepository repo = new SnapshotRepository(worldDir);
        Commit commit;
        try {
            commit = new WorldSnapshotter(repo).takeSnapshot(message, settings);
        } catch (WorldSnapshotter.UnchangedSnapshotException e) {
            deleteQuietly(shotTemp);
            notifyAsync(client, L10n.tr("gsave.message.snapshot_unchanged"));
            return;
        }
        storeScreenshot(worldDir, shotTemp, commit.id);
        notifyAsync(client, L10n.tr("gsave.message.snapshot_created",
                commit.shortId(), L10n.commitMessage(message)));
    }

    public static void requestRestore(MinecraftClient client, Commit commit, boolean createSafetySnapshot) {
        IntegratedServer server = client.getServer();
        if (server == null || client.world == null) {
            InfoUtils.sendVanillaMessage(L10n.text("gsave.message.restore_no_world"));
            return;
        }

        Path worldDir = getWorldDirectory(client);
        if (worldDir == null) {
            InfoUtils.sendVanillaMessage(L10n.text("gsave.message.no_world_dir"));
            return;
        }

        if (!createSafetySnapshot) {
            server.execute(() -> {
                if (new SnapshotRepository(worldDir).getCommit(commit.id) == null) {
                    notifyAsync(client, L10n.tr("gsave.message.restore_failed_missing"));
                    return;
                }
                client.execute(() -> beginRestoreTeardown(client, server, commit.id, worldDir));
            });
            return;
        }

        String worldName = worldName(server, worldDir);
        // 安全快照不触发旧快照清理，避免清理掉用户正要恢复的目标快照
        WorldSnapshotter.Settings safetySettings =
                snapshotSettings(worldName, Integer.MAX_VALUE, false);
        Path shotTemp = newTempShotPath(worldDir);

        captureScreenshot(client, shotTemp, () ->
                startSafetySnapshotThenRestore(client, server, worldDir, commit, safetySettings, shotTemp));
    }

    private static void startSafetySnapshotThenRestore(MinecraftClient client, IntegratedServer server, Path worldDir,
                                                       Commit commit, WorldSnapshotter.Settings safetySettings,
                                                       Path shotTemp) {
        if (SaveGitConfigs.isGameplayPriority()) {
            SnapshotStatus.begin(L10n.REASON_SAFETY);
            try {
                server.execute(() -> {
                    if (new SnapshotRepository(worldDir).getCommit(commit.id) == null) {
                        SnapshotStatus.end();
                        deleteQuietly(shotTemp);
                        notifyAsync(client, L10n.tr("gsave.message.restore_failed_missing"));
                        return;
                    }
                    try {
                        server.save(true, true, true);
                    } catch (Throwable t) {
                        SnapshotStatus.end();
                        deleteQuietly(shotTemp);
                        notifyAsync(client, L10n.tr("gsave.message.restore_cancelled", t.getMessage()));
                        return;
                    }

                    Thread thread = new Thread(() -> {
                        try {
                            runSnapshot(client, worldDir, L10n.REASON_SAFETY, safetySettings, shotTemp);
                        } catch (Throwable t) {
                            SaveGitMod.LOGGER.error("恢复前准备失败", t);
                            deleteQuietly(shotTemp);
                            notifyAsync(client, L10n.tr("gsave.message.restore_cancelled", t.getMessage()));
                            SnapshotStatus.end();
                            return;
                        }
                        SnapshotStatus.end();
                        client.execute(() -> beginRestoreTeardown(client, server, commit.id, worldDir));
                    }, "GSave-SafetySnapshot");
                    thread.setDaemon(true);
                    thread.start();
                });
            } catch (Throwable t) {
                SnapshotStatus.end();
                deleteQuietly(shotTemp);
                notifyAsync(client, L10n.tr("gsave.message.restore_cancelled", t.getMessage()));
            }
        } else {
            SnapshotStatus.begin(L10n.REASON_SAFETY);
            try {
                server.execute(() -> {
                    try {
                        if (new SnapshotRepository(worldDir).getCommit(commit.id) == null) {
                            notifyAsync(client, L10n.tr("gsave.message.restore_failed_missing"));
                            return;
                        }
                        server.save(true, true, true);
                        runSnapshot(client, worldDir, L10n.REASON_SAFETY, safetySettings, shotTemp);
                    } catch (Throwable t) {
                        SaveGitMod.LOGGER.error("恢复前准备失败", t);
                        deleteQuietly(shotTemp);
                        notifyAsync(client, L10n.tr("gsave.message.restore_cancelled", t.getMessage()));
                        return;
                    } finally {
                        SnapshotStatus.end();
                    }
                    client.execute(() -> beginRestoreTeardown(client, server, commit.id, worldDir));
                });
            } catch (Throwable t) {
                SnapshotStatus.end();
                deleteQuietly(shotTemp);
                notifyAsync(client, L10n.tr("gsave.message.restore_cancelled", t.getMessage()));
            }
        }
    }

    private static void beginRestoreTeardown(MinecraftClient client, IntegratedServer server,
                                             String commitId, Path worldDir) {
        pendingRestore = true;
        restoreStarted = false;
        pendingRestoreCommitId = commitId;
        pendingRestoreWorldDir = worldDir;
        pendingTeardownServer = server;
        teardownTicks = 0;

        // 原版 disconnectWithSavingScreen() 会一直等待 isStopping()。
        // 某些情况下服务端收不到 singleplayer owner 的登出事件，
        // 会导致“保存世界中”永久卡住，所以这里主动停止集成服务端。
        if (!server.isStopping()) {
            SaveGitMod.LOGGER.info("停止集成服务端以开始恢复快照");
            server.stop(false);
        }
        client.disconnectWithSavingScreen();
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
                    throw new IOException(L10n.tr("gsave.message.commit_missing", commitId));
                }
                new WorldRestorer(repo, worldDir).restore(commit);
            } catch (Throwable t) {
                SaveGitMod.LOGGER.error("恢复快照失败", t);
                RestoreProgress.fail(t.getMessage());
            }
        }, "GSave-Restore").start();
    }

    private static void onServerStopping(MinecraftServer server) {
        if (pendingRestore) {
            return;
        }
        if (!SaveGitConfigs.isEnabled() || !SaveGitConfigs.isDisconnectSnapshotEnabled()) {
            return;
        }

        SnapshotStatus.begin(L10n.REASON_DISCONNECT);
        try {
            Path worldDir = server.getSavePath(WorldSavePath.ROOT);
            server.save(true, true, true);
            // 退出快照不复用滚动截图：此时游戏已准备关闭，不提供截图
            new WorldSnapshotter(new SnapshotRepository(worldDir))
                    .takeSnapshot(L10n.REASON_DISCONNECT, snapshotSettings(
                            worldName(server, worldDir),
                            SaveGitConfigs.maxSnapshots(),
                            SaveGitConfigs.skipUnchangedAutomatic()));
        } catch (Throwable t) {
            SaveGitMod.LOGGER.error("创建退出快照失败", t);
        } finally {
            SnapshotStatus.end();
        }
    }

    /**
     * 延迟两帧再截图，并临时隐藏当前界面，避免把 GUI 拍进快照截图里。
     */
    private static void captureScreenshot(MinecraftClient client, Path temp, Runnable afterCapture) {
        if (PENDING_SHOTS.isEmpty()) {
            Screen current = client.currentScreen;
            if (current != null) {
                pendingShotRestoreScreen = current;
                client.setScreen(null);
            }
            pendingShotDelay = CLEAN_SHOT_DELAY_TICKS;
        }
        PENDING_SHOTS.addLast(new PendingShot(temp, afterCapture));
    }

    private static void processPendingShots(MinecraftClient client) {
        PendingShot pending = PENDING_SHOTS.peekFirst();
        if (pending == null) {
            return;
        }
        if (pendingShotDelay > 0) {
            pendingShotDelay--;
            return;
        }

        PENDING_SHOTS.pollFirst();
        takeScreenshotNow(client, pending.temp(), () -> {
            pending.afterCapture().run();
            if (PENDING_SHOTS.isEmpty() && pendingShotRestoreScreen != null) {
                Screen restore = pendingShotRestoreScreen;
                pendingShotRestoreScreen = null;
                client.execute(() -> client.setScreen(restore));
            }
        });
        if (!PENDING_SHOTS.isEmpty()) {
            pendingShotDelay = CLEAN_SHOT_DELAY_TICKS;
        }
    }

    private static void takeScreenshotNow(MinecraftClient client, Path temp, Runnable afterCapture) {
        try {
            ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), 1, image -> {
                try {
                    Path parent = temp.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    image.writeTo(temp);
                } catch (Throwable t) {
                    SaveGitMod.LOGGER.warn("写入快照截图失败", t);
                    deleteQuietly(temp);
                } finally {
                    try {
                        image.close();
                    } catch (Throwable ignored) {
                    }
                    afterCapture.run();
                }
            });
        } catch (Throwable t) {
            SaveGitMod.LOGGER.warn("截取快照截图失败", t);
            afterCapture.run();
        }
    }

    private static Path newTempShotPath(Path worldDir) {
        return SnapshotRepository.dataDir(worldDir).resolve(SnapshotRepository.DIR_SCREENSHOTS)
                .resolve("pending-" + System.currentTimeMillis() + "-" + SHOT_COUNTER.incrementAndGet() + ".png");
    }

    private static void storeScreenshot(Path worldDir, Path temp, String commitId) {
        try {
            if (temp == null || !Files.isRegularFile(temp)) {
                return;
            }
            Path dir = SnapshotRepository.dataDir(worldDir).resolve(SnapshotRepository.DIR_SCREENSHOTS);
            Files.createDirectories(dir);
            Files.move(temp, dir.resolve(commitId + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SaveGitMod.LOGGER.warn("保存快照截图失败", e);
            deleteQuietly(temp);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    private static WorldSnapshotter.Settings snapshotSettings(String worldName, int maxSnapshots,
                                                              boolean skipUnchanged) {
        return new WorldSnapshotter.Settings(
                SaveGitConfigs.snapshotRegion(),
                SaveGitConfigs.snapshotEntities(),
                SaveGitConfigs.snapshotPoi(),
                SaveGitConfigs.snapshotLevelDat(),
                SaveGitConfigs.snapshotPlayerdata(),
                maxSnapshots,
                skipUnchanged,
                SaveGitConfigs.protectManualSnapshots(),
                worldName,
                SaveGitMod.MC_VERSION
        );
    }

    private static String worldName(MinecraftServer server, Path worldDir) {
        if (server != null) {
            try {
                String name = server.getSaveProperties().getLevelName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } catch (Throwable ignored) {
            }
        }
        Path name = worldDir == null ? null : worldDir.getFileName();
        return name == null ? L10n.tr("gsave.gui.value.unknown_world") : name.toString();
    }

    private static void notifyAsync(MinecraftClient client, String message) {
        client.execute(() -> InfoUtils.sendVanillaMessage(Text.literal(message)));
    }
}
