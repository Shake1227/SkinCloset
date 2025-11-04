package shake1227.skincloset.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public class SkinProfile {

    private String name;
    private final UUID uuid;
    private final SkinData skinData;
    private String model;
    private transient GameProfile gameProfile;

    public static record SkinData(String value, String signature) {}

    public SkinProfile(String name, UUID uuid, String value, String signature, String model) {
        this.name = name;
        this.uuid = uuid;
        this.skinData = new SkinData(value, signature);
        this.model = (model == null || model.isEmpty()) ? "classic" : model;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }
    public String getModel() {
        return (this.model == null || this.model.isEmpty()) ? "classic" : this.model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public boolean isSlim() {
        return "slim".equals(this.getModel());
    }
    public GameProfile getGameProfile() {
        if (this.gameProfile == null) {
            this.gameProfile = new GameProfile(this.uuid, this.name);
            if (this.skinData != null) {
                this.gameProfile.getProperties().put("textures", new Property("textures", this.skinData.value(), this.skinData.signature()));
            }
        }
        return this.gameProfile;
    }

    public Optional<SkinData> getSkinData() {
        return Optional.ofNullable(this.skinData);
    }

    public Optional<String> getTextureUrl() {
        if (this.skinData == null) {
            return Optional.empty();
        }
        try {
            String decodedValue = new String(Base64.getDecoder().decode(this.skinData.value()));
            JsonObject json = JsonParser.parseString(decodedValue).getAsJsonObject();
            String url = json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            return Optional.of(url);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}