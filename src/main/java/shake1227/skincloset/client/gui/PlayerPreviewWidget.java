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
import net.minecraft.client.resources.skin.Skin; // このインポートが必要です
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

        // GameProfileからスキン情報を取得
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraft.getSkinManager().getInsecureSkinInformation(this.gameProfile);

        if (map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            // プレイヤーのスキンがある場合
            MinecraftProfileTexture texture = map.get(MinecraftProfileTexture.Type.SKIN);
            this.skinLocation = minecraft.getSkinManager().registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
            this.isSlim = texture.getMetadata("model") != null && texture.getMetadata("model").equals("slim");
        } else {
            // スキンがない場合（デフォルトスキン）
            UUID uuid = this.gameProfile != null ? this.gameProfile.getId() : Util.NIL_UUID;
            // Skin skin = DefaultPlayerSkin.forPlayer(uuid); -> 修正
            Skin skin = DefaultPlayerSkin.get(uuid); // 1.20.1では get(UUID) を使用
            this.skinLocation = skin.texture();
            this.isSlim = skin.model() == Skin.Model.SLIM; // PlayerSkin.Model -> Skin.Model
        }

        // 1.20.1: get(ModelLayers.PLAYER) または PLAYER_SLIM
        if (this.isSlim) {
            this.playerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        } else {
            this.playerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }

        this.playerModel.setAllVisible(true);
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if (this.skinLocation == null || this.playerModel == null) {
            return;
        }

        PoseStack pPoseStack = pGuiGraphics.pose();
        pPoseStack.pushPose();

        // ウィジェットの中央に移動
        pPoseStack.translate(this.getX() + this.width / 2.0, this.getY() + this.height * 0.9, 100.0);

        // スケール調整 (GUIのサイズに合わせる)
        float scale = this.height / 3.0f;
        pPoseStack.scale(-scale, -scale, scale); // XとYを反転させて正面を向かせる

        // 1.20.1: 逆さまになるZ軸回転を削除
        // pPoseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        // 要求：右斜め前を向く
        float yRot = 155.0F; // 約155度 (右斜め前)
        float xRot = 15.0F;  // 少し見下ろす

        // 要求：ホバーで回転
        if (this.isHoveredOrFocused()) {
            yRot = (Util.getMillis() / 20.0F) % 360.0F; // Y軸でゆっくり回転
        }

        pPoseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        pPoseStack.mulPose(Axis.XP.rotationDegrees(xRot));

        // ライティング
        Lighting.setupForEntityInInventory();

        // 描画
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.setShaderTexture(0, this.skinLocation);

        // 1.20.1: T-Pose (setupAnimはLivingEntityが必要なため使わない)
        // this.playerModel.setupAnim(0, 0, 0, 0, 0); // 使えない

        this.playerModel.renderToBuffer(
                pPoseStack,
                bufferSource.getBuffer(this.playerModel.renderType(this.skinLocation)),
                15728880, // Light (Full bright)
                OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f // Color (RGBA)
        );

        bufferSource.endBatch();
        pPoseStack.popPose();
        // Lighting.setupScreen(); -> 修正
        Lighting.setupForFlatItems(); // setupScreen() は 1.20.1 に存在しない
    }


    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
        // アクセシビリティ対応 (今回は省略)
    }
}

