package shake1227.skincloset.core;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shake1227.skincloset.client.gui.SkinClosetScreen;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Keybinds.OPEN_GUI_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new SkinClosetScreen());
        }
    }
}