package com.mingdai.savegit;

import com.mingdai.savegit.command.SaveGitCommands;
import com.mingdai.savegit.input.SaveGitKeyBindings;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SaveGitMod implements ClientModInitializer {
    public static final String MOD_ID = "gsave";
    public static final String MOD_NAME = "GSave";
    public static final String MOD_VERSION = "1.2.0";
    public static final String MC_VERSION = "1.21.8";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        SaveGitKeyBindings.init();
        InitializationHandler.getInstance().registerInitializationHandler(new SaveGitInitHandler());
        SaveGitSnapshotManager.init();
        SaveGitCommands.register();
    }
}
