package com.mingdai.savegit.preview;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.math.MathHelper;

//? if >=1.21.6 {
import net.minecraft.util.PlayerInput;
//?}

/**
 * 预览用的自由相机实体:与 Tweakeroo 的 freecam 类似,
 * 通过 MinecraftClient.setCameraEntity 把视角从玩家身上分离。
 */
public class PreviewCameraEntity extends ClientPlayerEntity {
    private PreviewCameraEntity(MinecraftClient client, ClientWorld world,
                                ClientPlayNetworkHandler networkHandler, StatHandler stats,
                                ClientRecipeBook recipeBook) {
        //? if >=1.21.6 {
        super(client, world, networkHandler, stats, recipeBook, PlayerInput.DEFAULT, false);
        //?} else {
        /*super(client, world, networkHandler, stats, recipeBook, false, false);
        *///?}
    }

    public static PreviewCameraEntity create(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        PreviewCameraEntity camera = new PreviewCameraEntity(client, client.world,
                player.networkHandler, player.getStatHandler(), player.getRecipeBook());
        camera.noClip = true;
        camera.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch());
        camera.setRotations(player.getYaw(), player.getPitch());
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
        this.lastRenderX = this.getX();
        this.lastRenderY = this.getY();
        this.lastRenderZ = this.getZ();

        //? if >=1.21.5 {
        this.lastX = this.getX();
        this.lastY = this.getY();
        this.lastZ = this.getZ();
        this.lastYaw = this.getYaw();
        this.lastPitch = this.getPitch();
        this.lastHeadYaw = this.headYaw;
        //?} else {
        /*this.prevX = this.getX();
        this.prevY = this.getY();
        this.prevZ = this.getZ();
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
        this.prevHeadYaw = this.headYaw;
        *///?}
    }

    /** 鼠标视角增量直接作用于预览相机。 */
    public void applyLook(double yawChange, double pitchChange) {
        float yaw = this.getYaw() + (float) yawChange * 0.15F;
        float pitch = MathHelper.clamp(this.getPitch() + (float) pitchChange * 0.15F, -90.0F, 90.0F);
        this.setRotations(yaw, pitch);
    }

    public void setRotations(float yaw, float pitch) {
        this.setYaw(yaw);
        this.setPitch(pitch);
        this.setHeadYaw(yaw);

        //? if >=1.21.5 {
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.lastHeadYaw = yaw;
        //?} else {
        /*this.prevYaw = yaw;
        this.prevPitch = pitch;
        this.prevHeadYaw = yaw;
        *///?}
    }

    public void moveBy(double dx, double dy, double dz) {
        this.setPosition(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }
}
