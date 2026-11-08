package com.mingdai.savegit.mixin;

import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 提供 WorldEntry#level (LevelSummary) 的访问入口。
 */
@Mixin(net.minecraft.client.gui.screen.world.WorldListWidget.WorldEntry.class)
public interface WorldEntryAccessor {
    @Accessor("level")
    LevelSummary savegit$getLevelSummary();
}
