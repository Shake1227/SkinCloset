package shake1227.skincloset.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.network.NetworkEvent;
import shake1227.skincloset.SkinCloset;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

import shake1227.skincloset.config.ServerConfig;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;

public class C2SChangeSkinPacket {

    private final String value;
    private final String signature;

    public C2SChangeSkinPacket(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    public static void encode(C2SChangeSkinPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.value != null ? pkt.value : "");
        buf.writeUtf(pkt.signature != null ? pkt.signature : "");
    }

    public static C2SChangeSkinPacket decode(FriendlyByteBuf buf) {
        return new C2SChangeSkinPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(C2SChangeSkinPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (pkt.value.isEmpty() || pkt.signature.isEmpty()) {
                SkinCloset.LOGGER.warn("Received C2SChangeSkinPacket with empty value or signature from {}.", player.getName().getString());
                return;
            }

            GameProfile profile = player.getGameProfile();
            PropertyMap properties = profile.getProperties();

            properties.removeAll("textures");
            properties.put("textures", new Property("textures", pkt.value, pkt.signature));

            PlayerList playerList = player.getServer().getPlayerList();
            ServerPlayer sPlayer = (ServerPlayer) player;

            sPlayer.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(sPlayer.getUUID())));
            sPlayer.getServer().getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(sPlayer)));

            sPlayer.connection.send(new ClientboundRespawnPacket(
                    sPlayer.level().dimensionTypeId(),
                    sPlayer.level().dimension(),
                    sPlayer.serverLevel().getServer().getWorldData().worldGenOptions().seed(),
                    sPlayer.gameMode.getGameModeForPlayer(),
                    sPlayer.gameMode.getPreviousGameModeForPlayer(),
                    sPlayer.level().isDebug(),
                    sPlayer.serverLevel().isFlat(),
                    (byte) 3,
                    sPlayer.getLastDeathLocation(),
                    sPlayer.getPortalCooldown()
            ));
            sPlayer.connection.teleport(sPlayer.getX(), sPlayer.getY(), sPlayer.getZ(), sPlayer.getYRot(), sPlayer.getXRot());
            sPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(sPlayer.getAbilities()));
            sPlayer.connection.send(new ClientboundSetCarriedItemPacket(sPlayer.getInventory().selected));

            sPlayer.inventoryMenu.broadcastChanges();

            ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(sPlayer.getId());
            ClientboundAddPlayerPacket addPacket = new ClientboundAddPlayerPacket(sPlayer);

            for (ServerPlayer otherPlayer : playerList.getPlayers()) {
                if (otherPlayer.getUUID().equals(sPlayer.getUUID())) {
                    continue;
                }
                otherPlayer.connection.send(removePacket);
                otherPlayer.connection.send(addPacket);
            }

            playerList.sendAllPlayerInfo(sPlayer);

            try {
                List<? extends String> consoleCommands = ServerConfig.INSTANCE.consoleCommand.get();
                List<? extends String> playerCommands = ServerConfig.INSTANCE.playerCommand.get();
                String playerName = player.getName().getString();
                String playerUUID = player.getUUID().toString();

                if (consoleCommands != null && !consoleCommands.isEmpty()) {
                    CommandSourceStack consoleSource = player.getServer().createCommandSourceStack();
                    for (String cmd : consoleCommands) {
                        if (cmd == null || cmd.isBlank()) continue;
                        String processedCmd = cmd.replace("@p", playerName).replace("@u", playerUUID);
                        player.getServer().getCommands().performPrefixedCommand(consoleSource, processedCmd);
                    }
                }

                if (playerCommands != null && !playerCommands.isEmpty()) {
                    CommandSourceStack playerSource = player.createCommandSourceStack();
                    for (String cmd : playerCommands) {
                        if (cmd == null || cmd.isBlank()) continue;
                        String processedCmd = cmd.replace("@p", playerName).replace("@u", playerUUID);
                        player.getServer().getCommands().performPrefixedCommand(playerSource, processedCmd);
                    }
                }
            } catch (Exception e) {
                SkinCloset.LOGGER.error("Failed to execute skin change commands for " + player.getName().getString(), e);
            }

            playerList.sendPlayerPermissionLevel(sPlayer);
            playerList.sendLevelInfo(sPlayer, sPlayer.serverLevel());
            sPlayer.connection.send(new ClientboundUpdateAttributesPacket(sPlayer.getId(), sPlayer.getAttributes().getSyncableAttributes()));
        });
        ctx.get().setPacketHandled(true);
    }
}