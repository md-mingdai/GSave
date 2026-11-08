package com.mingdai.savegit.command;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * 注册 /snapshot 客户端命令，用于手动触发一次快照。
 */
public final class SaveGitCommands {
    private SaveGitCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("snapshot")
                        .executes(context -> {
                            if (!SaveGitConfigs.isEnabled()) {
                                context.getSource().sendFeedback(
                                        Text.literal("SaveGit 总开关已关闭"));
                                return 0;
                            }
                            if (SaveGitSnapshotManager.getWorldDirectory(
                                    MinecraftClient.getInstance()) == null) {
                                context.getSource().sendFeedback(
                                        Text.literal("当前未加载单人世界"));
                                return 0;
                            }
                            SaveGitSnapshotManager.manualSnapshot();
                            context.getSource().sendFeedback(
                                    Text.literal("已请求创建快照，请稍候…"));
                            return 1;
                        })));
    }
}
