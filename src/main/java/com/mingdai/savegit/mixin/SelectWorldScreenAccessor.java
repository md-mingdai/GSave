package com.mingdai.savegit.mixin;

import net.minecraft.client.gui.screen.world.WorldListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 提供 SelectWorldScreen#levelList 的访问入口。
 */
@Mixin(net.minecraft.client.gui.screen.world.SelectWorldScreen.class)
public interface SelectWorldScreenAccessor {
    @Accessor("levelList")
    WorldListWidget savegit$getLevelList();
}
