package shake1227.skincloset.client.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

public class PlayerPreviewWidget extends AbstractWidget {

    private final GameProfile gameProfile;
    private ResourceLocation skinLocation;
    private PlayerModel<?> playerModel;
    private boolean isSlim = false;

    public PlayerPreviewWidget(int x, int y, int width, int height, GameProfile gameProfile) {
        super(x, y, width, height, Component.empty());
        this.gameProfile = gameProfile;
        this.loadSkin();
    }

    private void loadSkin() {
        Minecraft minecraft = Minecraft.getInstance();

        if (this.gameProfile != null && this.gameProfile.getProperties() != null) {
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraft.getSkinManager().getInsecureSkinInformation(this.gameFProfile);

            if (map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
                // プレイヤーのスキンがある場合
                MinecraftProfileTexture texture = map.get(MinecraftProfileTexture.Type.SKIN);
                this.skinLocation = minecraft.getSkinManager().registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
                this.isSlim = texture.getMetadata("model") != null && texture.getMetadata("model").equals("slim");
            } else {
                // スキン情報が無い場合 (デフォルトスキンへフォールバック)
                loadDefaultSkin();
            }
        } else {
            // gameProfile が null の場合 (デフォルトスキン)
            loadDefaultSkin();
        }

        // 1.20.1: get(ModelLayers.PLAYER) または PLAYER_SLIM
        if (this.isSlim) {
            this.playerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        } else {
            this.playerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }

        this.playerModel.setAllVisible(true);
    }

    // デフォルトスキン読み込み処理 (UUIDのnullチェック)
    private void loadDefaultSkin() {
        UUID playerUuid = null;

        if (this.gameProfile != null) {
            playerUuid = this.gameProfile.getId(); // ID が null の可能性あり
        }

        if (playerUuid == null) {
            playerUuid = Util.NIL_UUID; // NullPointerException を回避
        }

        this.skinLocation = DefaultPlayerSkin.getDefaultSkin(playerUuid);
        // getModelName() が正しい
        this.isSlim = DefaultPlayerSkin.getSkinModelName(playerUuid).equals("slim");
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if (this.skinLocation == null || this.playerModel == null) {
            return;
        }

        PoseStack pPoseStack = pGuiGraphics.pose();
        pPoseStack.pushPose();

        pPoseStack.translate(this.getX() + this.width / 2.0, this.getY() + this.height * 0.9, 100.0);

        // スケール調整 (GUIのサイズに合わせる)
        // (150の高さのウィジェットの場合、スケールは約50になる)
        float scale = this.height / 3.0f;
        pPoseStack.scale(-scale, -scale, scale); // XとYを反転させて正面を向かせる

        float yRot = 155.0F; // 右斜め前
        float xRot = 15.0F;  // 少し見下ろす

        if (this.isHoveredOrFocused()) {
            yRot = (Util.getMillis() / 20.0F) % 360.0F; // Y軸でゆっくり回転
        }

        pPoseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        pPoseStack.mulPose(Axis.XP.rotationDegrees(xRot));

        Lighting.setupForEntityInInventory();

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.setShaderTexture(0, this.skinLocation);

        this.playerModel.renderToBuffer(
                pPoseStack,
                bufferSource.getBuffer(this.playerModel.renderType(this.skinLocation)),
                15728880, // Light (Full bright)
                OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f // Color (RGBA)
        );

        bufferSource.endBatch();
        pPoseStack.popPose();
        Lighting.setupForFlatItems();
    }


    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
        // アクセシビリティ対応 (今回は省略)
    }
}