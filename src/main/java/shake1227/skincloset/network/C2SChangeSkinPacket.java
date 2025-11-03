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
// 属性同期パケットのインポートを追加
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

public class C2SChangeSkinPacket {

    private final String value;
    private final String signature;

    public C2SChangeSkinPacket(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    public static void encode(C2SChangeSkinPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.value);
        buf.writeUtf(pkt.signature);
    }

    public static C2SChangeSkinPacket decode(FriendlyByteBuf buf) {
        return new C2SChangeSkinPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(C2SChangeSkinPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // 1. プレイヤーのGameProfileに新しいスキン情報を適用
            GameProfile profile = player.getGameProfile();
            PropertyMap properties = profile.getProperties();

            properties.removeAll("textures");
            properties.put("textures", new Property("textures", pkt.value, pkt.signature));

            PlayerList playerList = player.getServer().getPlayerList();

            // 2. プレイヤー本人にリフレッシュを強制（リスポーンパケットを利用）
            ServerPlayer sPlayer = (ServerPlayer) player;
            sPlayer.connection.send(new ClientboundRespawnPacket(
                    sPlayer.level().dimensionTypeId(),
                    sPlayer.level().dimension(),
                    sPlayer.serverLevel().getServer().getWorldData().worldGenOptions().seed(),
                    sPlayer.gameMode.getGameModeForPlayer(),
                    sPlayer.gameMode.getPreviousGameModeForPlayer(),
                    sPlayer.level().isDebug(),
                    // 1.20.1: sPlayer.serverLevel() (ServerLevel) の isFlatLevel() を呼び出す
                    sPlayer.serverLevel().isFlat(),
                    (byte) 3, // Keep all data
                    sPlayer.getLastDeathLocation(),
                    sPlayer.getPortalCooldown()
            ));

            // 3. プレイヤーの位置と状態を再同期
            sPlayer.connection.teleport(sPlayer.getX(), sPlayer.getY(), sPlayer.getZ(), sPlayer.getYRot(), sPlayer.getXRot());
            sPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(sPlayer.getAbilities()));
            sPlayer.connection.send(new ClientboundSetCarriedItemPacket(sPlayer.getInventory().selected));

            sPlayer.inventoryMenu.broadcastChanges();

            // 4. プレイヤーのトラッキングを更新 (REMOVE と ADD でスキン情報を更新)
            sPlayer.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(sPlayer.getUUID())));
            sPlayer.getServer().getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(sPlayer)));

            playerList.sendPlayerPermissionLevel(sPlayer);
            playerList.sendLevelInfo(sPlayer, sPlayer.serverLevel());
            playerList.sendAllPlayerInfo(sPlayer);

            // 1.20.1: sPlayer.getAttributes().syncTo(sPlayer); をバニラのパケット送信に置き換え
            // プレイヤーの全属性（体力など）をクライアントに同期させる
            sPlayer.connection.send(new ClientboundUpdateAttributesPacket(sPlayer.getId(), sPlayer.getAttributes().getSyncableAttributes()));
        });
        ctx.get().setPacketHandled(true);
    }
}

