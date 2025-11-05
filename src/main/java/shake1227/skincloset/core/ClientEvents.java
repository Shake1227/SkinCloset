package shake1227.skincloset.core;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shake1227.skincloset.SkinCloset;
import shake1227.skincloset.client.gui.SkinClosetScreen;
import shake1227.skincloset.network.C2SChangeSkinPacket;
import shake1227.skincloset.network.PacketRegistry;
import shake1227.skincloset.skin.LastSkinCache;

public class ClientEvents {

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (Keybinds.OPEN_GUI_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new SkinClosetScreen());
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        LastSkinCache.LastSkinData lastSkin = LastSkinCache.load();
        if (lastSkin != null) {
            SkinCloset.LOGGER.info("Applying persistent skin from last session.");
            PacketRegistry.CHANNEL.sendToServer(new C2SChangeSkinPacket(lastSkin.value(), lastSkin.signature()));
        }
    }
}