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
import shake1227.skincloset.skin.SkinProfile;
import java.util.Map;
import java.util.UUID;

public class PlayerPreviewWidget extends AbstractWidget {
    private final SkinProfile skinProfile;
    private ResourceLocation skinLocation;
    private PlayerModel<?> playerModel;
    private boolean isSlim = false;
    public PlayerPreviewWidget(int x, int y, int width, int height, SkinProfile skinProfile) {
        super(x, y, width, height, Component.empty());
        this.skinProfile = skinProfile;
        this.loadSkin();
    }

    private void loadSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        GameProfile gameProfile = this.skinProfile.getGameProfile();

        if (gameProfile != null && gameProfile.getProperties() != null) {
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraft.getSkinManager().getInsecureSkinInformation(gameProfile);

            if (map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
                MinecraftProfileTexture texture = map.get(MinecraftProfileTexture.Type.SKIN);
                this.skinLocation = minecraft.getSkinManager().registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
                this.isSlim = this.skinProfile.isSlim();
            } else {
                loadDefaultSkin(gameProfile.getId());
            }
        } else {
            loadDefaultSkin(Util.NIL_UUID);
        }
        if (this.isSlim) {
            this.playerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        } else {
            this.playerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }

        this.playerModel.setAllVisible(true);
    }
    private void loadDefaultSkin(UUID playerUuid) {
        if (playerUuid == null) {
            playerUuid = Util.NIL_UUID;
        }
        this.isSlim = this.skinProfile.isSlim();
        this.skinLocation = DefaultPlayerSkin.getDefaultSkin(playerUuid);
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if (this.skinLocation == null || this.playerModel == null) {
            return;
        }

        PoseStack pPoseStack = pGuiGraphics.pose();
        pPoseStack.pushPose();
        pPoseStack.translate(this.getX() + this.width / 2.0, this.getY() + this.height * 0.45, 100.0);

        float scale = this.height / 2.8f;
        pPoseStack.scale(scale, scale, scale);
        float yRot = 180.0F + 25.0F;
        float xRot = 0.0F;

        if (this.isHoveredOrFocused()) {
            yRot = 180.0F + (Util.getMillis() / 20.0F) % 360.0F;
        }

        pPoseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        pPoseStack.mulPose(Axis.XP.rotationDegrees(xRot));

        Lighting.setupForEntityInInventory();

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.setShaderTexture(0, this.skinLocation);
        this.playerModel.young = false;
        this.playerModel.hat.visible = true;
        this.playerModel.jacket.visible = true;
        this.playerModel.leftPants.visible = true;
        this.playerModel.rightPants.visible = true;
        this.playerModel.leftSleeve.visible = true;
        this.playerModel.rightSleeve.visible = true;

        this.playerModel.renderToBuffer(
                pPoseStack,
                bufferSource.getBuffer(this.playerModel.renderType(this.skinLocation)),
                15728880,
                OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f
        );

        bufferSource.endBatch();
        pPoseStack.popPose();
    }


    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
    }
}