package com.mingdai.savegit.command;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiSnapshotList;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fi.dy.masa.malilib.gui.GuiBase;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.nio.file.Path;

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
                                            return CommandSource.suggestMatching(
                                                    new SnapshotRepository(worldDir).listCommits()
                                                            .stream().map(Commit::shortId).toList(),
                                                    builder);
                                        })
                                        .executes(context -> restore(context, true))
                                        .then(ClientCommandManager.argument(
                                                        "safety", BoolArgumentType.bool())
                                                .executes(context -> restore(context,
                                                        BoolArgumentType.getBool(context, "safety"))))))));
    }

    private static int help(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.literal("SaveGit 命令帮助："));
        context.getSource().sendFeedback(Text.literal("/snapshot create —— 手动创建一次快照"));
        context.getSource().sendFeedback(Text.literal("/snapshot view —— 打开快照列表"));
        context.getSource().sendFeedback(
                Text.literal("/snapshot restore [快照ID] [true/false] —— 恢复快照，"
                        + "true 表示恢复前创建安全快照（默认），false 表示不创建"));
        return 1;
    }

    private static int create(CommandContext<FabricClientCommandSource> context) {
        if (!SaveGitConfigs.isEnabled()) {
            context.getSource().sendError(Text.literal("SaveGit 总开关已关闭"));
            return 0;
        }
        if (SaveGitSnapshotManager.getWorldDirectory(MinecraftClient.getInstance()) == null) {
            context.getSource().sendError(Text.literal("当前未加载单人世界"));
            return 0;
        }
        SaveGitSnapshotManager.manualSnapshot();
        context.getSource().sendFeedback(Text.literal("已请求创建快照，请稍候…"));
        return 1;
    }

    private static int view(CommandContext<FabricClientCommandSource> context) {
        GuiBase.openGui(new GuiSnapshotList());
        return 1;
    }

    private static int restore(CommandContext<FabricClientCommandSource> context, boolean safety) {
        MinecraftClient client = MinecraftClient.getInstance();
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(client);
        if (worldDir == null) {
            context.getSource().sendError(Text.literal("当前未加载单人世界"));
            return 0;
        }
        if (!SaveGitConfigs.isEnabled()) {
            context.getSource().sendError(Text.literal("SaveGit 总开关已关闭"));
            return 0;
        }

        String input = StringArgumentType.getString(context, "id");
        Commit commit = new SnapshotRepository(worldDir).listCommits().stream()
                .filter(c -> c.id.startsWith(input) || c.shortId().equals(input))
                .findFirst().orElse(null);
        if (commit == null) {
            context.getSource().sendError(Text.literal("找不到快照 #" + input));
            return 0;
        }

        client.setScreen(null);
        SaveGitSnapshotManager.requestRestore(client, commit, safety);
        context.getSource().sendFeedback(Text.literal("正在恢复到快照 #" + commit.shortId()
                + (safety ? "（恢复前会自动创建安全快照）" : "（不创建安全快照）")));
        return 1;
    }
}
