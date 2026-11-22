package com.mingdai.savegit.preview;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

//? if >=26.3 {
import net.minecraft.client.player.ItemActivation;
//?}

/**
 * 预览用的自由相机实体:与 Tweakeroo 的 freecam 类似,
 * 通过 Minecraft.setCameraEntity 把视角从玩家身上分离。
 */
public class PreviewCameraEntity extends LocalPlayer {
    private PreviewCameraEntity(Minecraft client, ClientLevel level,
                                ClientPacketListener connection, StatsCounter stats,
                                ClientRecipeBook recipeBook) {
        //? if >=26.3 {
        super(client, level, connection, stats, recipeBook, Input.EMPTY, false,
                ChatAbilities.NO_RESTRICTIONS, new ItemActivation());
        //?} else {
        /*super(client, level, connection, stats, recipeBook, Input.EMPTY, false,
                ChatAbilities.NO_RESTRICTIONS);
        *///?}
    }

    public static PreviewCameraEntity create(Minecraft client) {
        LocalPlayer player = client.player;
        PreviewCameraEntity camera = new PreviewCameraEntity(client, client.level,
                player.connection, player.getStats(), player.getRecipeBook());
        camera.noPhysics = true;
        Vec3 pos = player.position();
        camera.setPosRaw(pos.x, pos.y, pos.z);
        camera.setRotations(player.getYRot(), player.getXRot());
        camera.setDeltaMovement(Vec3.ZERO);
        return camera;
    }

    @Override
    public boolean isSpectator() {
        return true;
    }

    /** 部分模组会读取实体 id,沿用原相机实体的 id 以避免异常。 */
    @Override
    public int getId() {
        return PreviewManager.getInstance().originalEntityId();
    }

    /** 在移动前记录上一帧的位置/旋转,让相机插值保持平滑。 */
    public void updatePrev() {
        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.yHeadRotO = this.yHeadRot;
    }

    /** 鼠标视角增量直接作用于预览相机。 */
    public void applyLook(double yawChange, double pitchChange) {
        float yaw = this.getYRot() + (float) yawChange * 0.15F;
        float pitch = Mth.clamp(this.getXRot() + (float) pitchChange * 0.15F, -90.0F, 90.0F);
        this.setRotations(yaw, pitch);
    }

    public void setRotations(float yaw, float pitch) {
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yHeadRot = yaw;

        this.yRotO = yaw;
        this.xRotO = pitch;
        this.yHeadRotO = yaw;
    }

    public void moveBy(double dx, double dy, double dz) {
        this.setPosRaw(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }
}
