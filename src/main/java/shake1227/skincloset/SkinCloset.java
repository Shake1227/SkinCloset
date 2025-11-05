package shake1227.skincloset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shake1227.skincloset.client.ClientOnlySetup;
import shake1227.skincloset.client.gui.SkinClosetScreen;
import shake1227.skincloset.core.Keybinds;
import shake1227.skincloset.core.ClientEvents;
import shake1227.skincloset.network.PacketRegistry;
import shake1227.skincloset.skin.SkinCache;

@Mod(Constants.MOD_ID)
public class SkinCloset {

    public static final Logger LOGGER = LogManager.getLogger();

    public static boolean isModernNotificationLoaded = false;

    public SkinCloset() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(this::clientSetup);
            ClientOnlySetup.registerClientEvents(modEventBus);
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketRegistry.register();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SkinCache.loadProfiles();

            isModernNotificationLoaded = ModList.get().isLoaded("modernnotification");
            if (isModernNotificationLoaded) {
                LOGGER.info("ModernNotification detected! Rerouting chat messages to notifications.");
            } else {
                LOGGER.info("ModernNotification not found. Using default chat messages.");
            }
        });
    }
}