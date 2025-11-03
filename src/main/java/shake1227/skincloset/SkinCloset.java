package shake1227.skincloset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shake1227.skincloset.client.gui.SkinClosetScreen;
import shake1227.skincloset.core.Keybinds;
import shake1227.skincloset.network.PacketRegistry;
import shake1227.skincloset.skin.SkinCache;

@Mod(Constants.MOD_ID)
public class SkinCloset {

    public static final Logger LOGGER = LogManager.getLogger();

    public SkinCloset() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // クライアントセットアップ
        modEventBus.addListener(this::clientSetup);

        // Forgeのイベントバス（キー入力など）
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PacketRegistry.register();
            SkinCache.loadProfiles(); // 正しいメソッド名
        });
    }

    // Dist.CLIENTを指定して、クライアントサイドでのみこのイベントをリッスンする
    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (Keybinds.OPEN_GUI.consumeClick()) {
                Minecraft.getInstance().setScreen(new SkinClosetScreen());
            }
        }
    }
}

