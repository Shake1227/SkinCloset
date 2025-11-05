package shake1227.skincloset.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import shake1227.skincloset.SkinCloset;
import shake1227.skincloset.core.ClientEvents;
import shake1227.skincloset.core.Keybinds;

public class ClientOnlySetup {

    public static void registerClientEvents(IEventBus modEventBus) {
        SkinCloset.LOGGER.info("Registering client-only event listeners for SkinCloset...");

        modEventBus.register(Keybinds.class);
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }
}