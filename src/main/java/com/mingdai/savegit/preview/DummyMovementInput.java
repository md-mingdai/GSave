package com.mingdai.savegit.preview;

import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;

/**
 * 预览期间替换玩家输入,避免玩家跟随相机移动。
 */
public class DummyMovementInput extends KeyboardInput {
    public DummyMovementInput(GameOptions options) {
        super(options);
    }

    //? if >=1.21.4 {
    @Override
    public void tick() {
        // 预览期间不接收任何移动输入
    }
    //?} else {
    /*@Override
    public void tick(boolean slowDown, float slowDownFactor) {
        // 预览期间不接收任何移动输入
    }
    *///?}
}
