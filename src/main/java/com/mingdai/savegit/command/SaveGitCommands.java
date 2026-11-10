package com.mingdai.savegit.command;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiSnapshotList;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.util.L10n;
import com.mingdai.savegit.util.SnapshotTime;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fi.dy.masa.malilib.gui.GuiBase;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 注册 /snapshot 客户端命令，包含 create、restore、view 三个子命令。
 */
public final class SaveGitCommands {
    private SaveGitCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("snapshot")
                        .executes(SaveGitCommands::help)
                        .then(ClientCommandManager.literal("create")
                                .executes(SaveGitCommands::create))
                        .then(ClientCommandManager.literal("view")
                                .executes(SaveGitCommands::view))
                        .then(ClientCommandManager.literal("list")
                                .executes(SaveGitCommands::list))
                        .then(ClientCommandManager.literal("note")
                                .then(ClientCommandManager.argument(
                                                "id", StringArgumentType.word())
                                        .then(ClientCommandManager.argument(
                                                        "text", StringArgumentType.greedyString())
                                                .executes(context -> note(context,
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "text"))))))
                        .then(ClientCommandManager.literal("pin")
                                .then(ClientCommandManager.argument(
                                                "id", StringArgumentType.word())
                                        .executes(context -> togglePin(context,
                                                StringArgumentType.getString(context, "id")))))
                        .then(ClientCommandManager.literal("prune")
                                .executes(SaveGitCommands::prune))
                        .then(ClientCommandManager.literal("export")
                                .executes(context -> export(context, null))
                                .then(ClientCommandManager.argument(
                                                "path", StringArgumentType.greedyString())
                                        .executes(context -> export(context,
                                                StringArgumentType.getString(context, "path")))))
                        .then(ClientCommandManager.literal("import")
                                .then(ClientCommandManager.argument(
                                                "path", StringArgumentType.greedyString())
                                        .executes(context -> importArchive(context,
                                                StringArgumentType.getString(context, "path")))))
                        .then(ClientCommandManager.literal("restore")
                                .executes(SaveGitCommands::view)
                                .then(ClientCommandManager.argument(
                                                "id", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            Path worldDir = SaveGitSnapshotManager.getWorldDirectory(
                                                    MinecraftClient.getInstance());
                                            if (worldDir == null) {
                                                return builder.buildFuture();
                                            }
                                            String remaining = builder.getRemainingLowerCase();
                                            for (Commit commit : new SnapshotRepository(worldDir)
                                                    .listCommits()) {
                                                if (commit.shortId().startsWith(remaining)) {
                                                    builder.suggest(commit.shortId(),
                                                            Text.literal(GuiSnapshotList.formatTime(
                                                                            commit.timestamp))
                                                                    .append(Text.literal("  "
                                                                            + (commit.message == null
                                                                            ? "" : commit.message)))
                                                                    .formatted(Formatting.GRAY));
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> restore(context, true))
                                        .then(ClientCommandManager.argument(
                                                        "safety", BoolArgumentType.bool())
                                                .suggests((context, builder) ->
                                                        builder.suggest("true")
                                                                .suggest("false")
                                                                .buildFuture())
                                                .executes(context -> restore(context,
                                                        BoolArgumentType.getBool(context, "safety"))))))));
    }

    private static int help(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(
                L10n.text("gsave.command.help.header").formatted(Formatting.GOLD));
        context.getSource().sendFeedback(commandLine(
                "/snapshot create", L10n.tr("gsave.command.help.create")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot view", L10n.tr("gsave.command.help.view")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot list", L10n.tr("gsave.command.help.list")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot note <id> <text>", L10n.tr("gsave.command.help.note")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot pin <id>", L10n.tr("gsave.command.help.pin")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot prune", L10n.tr("gsave.command.help.prune")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot export [path]", L10n.tr("gsave.command.help.export")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot import <path>", L10n.tr("gsave.command.help.import")));
        context.getSource().sendFeedback(commandLine(
                "/snapshot restore [id] [true/false]",
                L10n.tr("gsave.command.help.restore")));
        return 1;
    }

    private static Text error(String message) {
        return Text.literal(message).formatted(Formatting.RED);
    }

    private static Text commandLine(String command, String description) {
        return Text.literal(command).formatted(Formatting.AQUA)
                .append(Text.literal(" — ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(description).formatted(Formatting.GRAY));
    }

    private static int create(CommandContext<FabricClientCommandSource> context) {
        if (!SaveGitConfigs.isEnabled()) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.disabled")));
            return 0;
        }
        if (SaveGitSnapshotManager.getWorldDirectory(MinecraftClient.getInstance()) == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }
        SaveGitSnapshotManager.manualSnapshot();
        context.getSource().sendFeedback(
                L10n.text("gsave.command.snapshot_requested").formatted(Formatting.GREEN));
        return 1;
    }

    private static int view(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        // 命令执行时聊天栏随后会 setScreen(null)，延后一帧再打开界面
        client.execute(() -> GuiBase.openGui(new GuiSnapshotList()));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> context) {
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(MinecraftClient.getInstance());
        if (worldDir == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }
        List<Commit> commits = new SnapshotRepository(worldDir).listCommits();
        if (commits.isEmpty()) {
            context.getSource().sendFeedback(L10n.text("gsave.gui.message.no_snapshots"));
            return 1;
        }

        context.getSource().sendFeedback(L10n.text("gsave.command.list.header",
                String.valueOf(commits.size())).formatted(Formatting.GOLD));
        int shown = 0;
        for (Commit commit : commits) {
            if (shown >= 15) {
                context.getSource().sendFeedback(L10n.text("gsave.command.list.more",
                        String.valueOf(commits.size() - shown)).formatted(Formatting.DARK_GRAY));
                break;
            }
            String message = commit.note != null && !commit.note.isEmpty()
                    ? commit.note : L10n.commitMessage(commit.message);
            context.getSource().sendFeedback(L10n.text("gsave.command.list.line",
                            commit.shortId(), SnapshotTime.full(commit.timestamp), message)
                    .formatted(Formatting.GRAY));
            shown++;
        }
        return 1;
    }

    private static int note(CommandContext<FabricClientCommandSource> context,
                            String input, String text) {
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(MinecraftClient.getInstance());
        if (worldDir == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }
        Commit commit = findCommit(worldDir, input);
        if (commit == null) {
            context.getSource().sendError(error(
                    L10n.tr("gsave.command.error.snapshot_not_found", input)));
            return 0;
        }
        try {
            new SnapshotRepository(worldDir).setNote(commit.id, text);
        } catch (IOException e) {
            context.getSource().sendError(error(
                    L10n.tr("gsave.message.note_failed", e.getMessage())));
            return 0;
        }
        context.getSource().sendFeedback(L10n.text("gsave.command.note_saved", commit.shortId())
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int togglePin(CommandContext<FabricClientCommandSource> context, String input) {
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(MinecraftClient.getInstance());
        if (worldDir == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }
        Commit commit = findCommit(worldDir, input);
        if (commit == null) {
            context.getSource().sendError(error(
                    L10n.tr("gsave.command.error.snapshot_not_found", input)));
            return 0;
        }
        boolean pinned = !commit.pinned;
        try {
            new SnapshotRepository(worldDir).setPinned(commit.id, pinned);
        } catch (IOException e) {
            context.getSource().sendError(error(
                    L10n.tr("gsave.message.pin_failed", e.getMessage())));
            return 0;
        }
        context.getSource().sendFeedback(L10n.text(
                        pinned ? "gsave.command.pin_on" : "gsave.command.pin_off",
                        commit.shortId())
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int prune(CommandContext<FabricClientCommandSource> context) {
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(MinecraftClient.getInstance());
        if (worldDir == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }
        int removed = new SnapshotRepository(worldDir).cleanupOldSnapshots(
                SaveGitConfigs.maxSnapshots(), SaveGitConfigs.protectManualSnapshots());
        context.getSource().sendFeedback(L10n.text("gsave.command.prune_done",
                String.valueOf(removed)).formatted(Formatting.GREEN));
        return 1;
    }

    private static int export(CommandContext<FabricClientCommandSource> context, String path) {
        MinecraftClient client = MinecraftClient.getInstance();
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(client);
        if (worldDir == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }

        Path target;
        if (path == null || path.isBlank()) {
            String world = worldDir.getFileName() == null
                    ? "world" : worldDir.getFileName().toString();
            target = client.runDirectory.toPath().resolve("gsave-exports")
                    .resolve("gsave-" + sanitize(world) + "-"
                            + System.currentTimeMillis() + ".zip");
        } else {
            target = resolvePath(client, path);
        }

        try {
            com.mingdai.savegit.snapshot.RepositoryArchive.ExportResult result =
                    com.mingdai.savegit.snapshot.RepositoryArchive.exportAll(worldDir, target);
            context.getSource().sendFeedback(L10n.text("gsave.command.export_done",
                            String.valueOf(result.commits()), target.toString())
                    .formatted(Formatting.GREEN));
            return 1;
        } catch (IOException e) {
            context.getSource().sendError(error(
                    L10n.tr("gsave.command.error.op_failed", e.getMessage())));
            return 0;
        }
    }

    private static int importArchive(CommandContext<FabricClientCommandSource> context, String path) {
        MinecraftClient client = MinecraftClient.getInstance();
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(client);
        if (worldDir == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }
        Path source = resolvePath(client, path);
        try {
            com.mingdai.savegit.snapshot.RepositoryArchive.ImportResult result =
                    com.mingdai.savegit.snapshot.RepositoryArchive.importArchive(worldDir, source);
            context.getSource().sendFeedback(L10n.text("gsave.command.import_done",
                            String.valueOf(result.commits()), String.valueOf(result.blobs()))
                    .formatted(Formatting.GREEN));
            return 1;
        } catch (IOException e) {
            context.getSource().sendError(error(
                    L10n.tr("gsave.command.error.op_failed", e.getMessage())));
            return 0;
        }
    }

    private static Path resolvePath(MinecraftClient client, String path) {
        Path resolved = Path.of(path);
        return resolved.isAbsolute() ? resolved : client.runDirectory.toPath().resolve(resolved);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Commit findCommit(Path worldDir, String input) {        return new SnapshotRepository(worldDir).listCommits().stream()
                .filter(commit -> commit.id.startsWith(input) || commit.shortId().equals(input))
                .findFirst().orElse(null);
    }

    private static int restore(CommandContext<FabricClientCommandSource> context, boolean safety) {
        MinecraftClient client = MinecraftClient.getInstance();
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(client);
        if (worldDir == null) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.no_world")));
            return 0;
        }
        if (!SaveGitConfigs.isEnabled()) {
            context.getSource().sendError(error(L10n.tr("gsave.command.error.disabled")));
            return 0;
        }

        String input = StringArgumentType.getString(context, "id");
        Commit commit = new SnapshotRepository(worldDir).listCommits().stream()
                .filter(c -> c.id.startsWith(input) || c.shortId().equals(input))
                .findFirst().orElse(null);
        if (commit == null) {
            context.getSource().sendError(error(
                    L10n.tr("gsave.command.error.snapshot_not_found", input)));
            return 0;
        }

        client.setScreen(null);
        SaveGitSnapshotManager.requestRestore(client, commit, safety);
        context.getSource().sendFeedback(L10n.text("gsave.command.restoring", commit.shortId())
                .formatted(Formatting.GREEN)
                .append(L10n.text(safety
                                ? "gsave.command.restoring.safety"
                                : "gsave.command.restoring.no_safety")
                        .formatted(safety ? Formatting.GRAY : Formatting.YELLOW)));
        return 1;
    }
}
