package shake1227.skincloset.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import shake1227.skincloset.Constants;

public class PacketRegistry {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++,
                C2SChangeSkinPacket.class,
                C2SChangeSkinPacket::encode,
                C2SChangeSkinPacket::decode,
                C2SChangeSkinPacket::handle
        );
    }
}