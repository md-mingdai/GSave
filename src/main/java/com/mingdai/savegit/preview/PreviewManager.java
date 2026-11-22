package com.mingdai.savegit.preview;

import com.mingdai.savegit.compat.ClientCompat;
import com.mingdai.savegit.snapshot.ChunkPreviewMap;
import com.mingdai.savegit.snapshot.ChunkSelection;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 恢复预览的客户端状态机:接管相机、处理移动与鼠标视角,并在 ESC/界面打开时退出。
 */
public final class PreviewManager {
    private static final PreviewManager INSTANCE = new PreviewManager();
    private static final double WALK_SPEED = 0.45;
    private static final double SPRINT_MULTIPLIER = 3.0;

    private Minecraft client;
    private PreviewCameraEntity camera;
    private Entity originalCamera;
    private boolean originalCull;
    private boolean originalNoGravity;
    private double playerX;
    private double playerY;
    private double playerZ;
    private int originalEntityId;
    private Screen returnScreen;
    private PreviewSession session;
    private volatile boolean active;

    private PreviewManager() {
    }

    public static PreviewManager getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return this.active;
    }

    public PreviewSession session() {
        return this.session;
    }

    public ChunkPreviewMap previewMap() {
        return this.session == null ? null : this.session.previewMap();
    }

    public ChunkSelection selection() {
        return this.session == null ? null : this.session.selection();
    }

    public int originalEntityId() {
        return this.originalEntityId;
    }

    /**
     * 进入预览。调用方通常已经打开了确认界面,这里会关闭它并接管相机。
     */
    public boolean start(Minecraft client, PreviewSession session, Screen returnScreen) {
        if (this.active) {
            this.stop(false);
        }
        if (client.player == null || client.level == null) {
            return false;
        }

        this.client = client;
        this.session = session;
        this.returnScreen = returnScreen;
        this.originalCamera = client.getCameraEntity();
        this.originalCull = client.smartCull;
        this.originalNoGravity = client.player.hasNoGravity();
        this.originalEntityId = client.player.getId();

        this.playerX = client.player.getX();
        this.playerY = client.player.getY();
        this.playerZ = client.player.getZ();

        this.camera = PreviewCameraEntity.create(client);
        client.setCameraEntity(this.camera);
        client.smartCull = false;
        client.player.setNoGravity(true);
        client.player.setDeltaMovement(Vec3.ZERO);
        this.active = true;
        ClientCompat.setScreen(client, null);

        session.computeAsync();
        return true;
    }

    /** 退出预览并恢复玩家相机与状态。reopen 为 true 时返回确认界面。 */
    public void stop(boolean reopen) {
        if (!this.active) {
            return;
        }
        this.active = false;

        Minecraft mc = this.client;
        Screen back = this.returnScreen;
        if (mc != null) {
            if (mc.level != null && mc.player != null) {
                mc.setCameraEntity(this.originalCamera);
                mc.smartCull = this.originalCull;
                mc.player.setNoGravity(this.originalNoGravity);
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.setPos(this.playerX, this.playerY, this.playerZ);
            }
            if (reopen && back != null) {
                GuiBase.openGui(back);
            }
        }

        this.camera = null;
        this.session = null;
        this.returnScreen = null;
        this.client = null;
    }

    /** END_CLIENT_TICK:按键移动相机,并检测 ESC / 界面打开 / 世界卸载。 */
    public void tick(Minecraft mc) {
        if (!this.active) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            this.stop(false);
            return;
        }
        if (ClientCompat.currentScreen(mc) != null) {
            this.stop(true);
            return;
        }

        // 冻结玩家:预览只是观察,不应该让玩家跟着移动或掉落
        mc.player.setPos(this.playerX, this.playerY, this.playerZ);
        mc.player.setDeltaMovement(Vec3.ZERO);

        PreviewCameraEntity cam = this.camera;
        if (cam == null) {
            this.stop(false);
            return;
        }

        cam.updatePrev();

        double forward = key(mc.options.keyUp) - key(mc.options.keyDown);
        double strafe = key(mc.options.keyRight) - key(mc.options.keyLeft);
        double up = key(mc.options.keyJump) - key(mc.options.keyShift);
        if (forward == 0.0 && strafe == 0.0 && up == 0.0) {
            return;
        }

        double speed = mc.options.keySprint.isDown() ? WALK_SPEED * SPRINT_MULTIPLIER : WALK_SPEED;
        double yaw = Math.toRadians(cam.getYRot());
        double dx = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double dz = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        double length = Math.sqrt(dx * dx + dz * dz + up * up);
        if (length > 1.0) {
            dx /= length;
            dz /= length;
            up /= length;
        }
        cam.moveBy(dx * speed, up * speed, dz * speed);
    }

    private static double key(KeyMapping binding) {
        return binding.isDown() ? 1.0 : 0.0;
    }

    /** 鼠标视角:预览激活时把玩家视角增量转给预览相机并拦截原版处理。 */
    public boolean redirectLook(double yawChange, double pitchChange) {
        if (!this.active) {
            return false;
        }
        PreviewCameraEntity cam = this.camera;
        if (cam == null) {
            return false;
        }
        cam.applyLook(yawChange, pitchChange);
        return true;
    }

    /** 断开连接/世界切换时的强制清理,不尝试打开任何界面。 */
    public void reset() {
        this.stop(false);
    }
}
