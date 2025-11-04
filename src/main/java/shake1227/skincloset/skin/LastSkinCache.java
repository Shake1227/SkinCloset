package shake1227.skincloset.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import shake1227.skincloset.SkinCloset;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class LastSkinCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("skincloset");
    private static final Path LAST_SKIN_FILE = CONFIG_DIR.resolve("last_skin.json");

    public static record LastSkinData(String value, String signature) {}

    public static void save(SkinProfile profile) {
        Optional<SkinProfile.SkinData> dataOpt = profile.getSkinData();
        if (dataOpt.isEmpty()) {
            SkinCloset.LOGGER.warn("Attempted to save persistent skin for {} but it has no skin data.", profile.getName());
            return;
        }

        SkinProfile.SkinData data = dataOpt.get();
        LastSkinData lastSkin = new LastSkinData(data.value(), data.signature());

        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(LAST_SKIN_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(lastSkin, writer);
            }
        } catch (Exception e) {
            SkinCloset.LOGGER.error("Failed to save last applied skin", e);
        }
    }

    public static LastSkinData load() {
        if (Files.exists(LAST_SKIN_FILE)) {
            try (Reader reader = Files.newBufferedReader(LAST_SKIN_FILE, StandardCharsets.UTF_8)) {
                LastSkinData loaded = GSON.fromJson(reader, LastSkinData.class);
                if (loaded != null && loaded.value() != null && !loaded.value().isEmpty()) {
                    return loaded;
                }
            } catch (Exception e) {
                SkinCloset.LOGGER.error("Failed to load last applied skin", e);
            }
        }
        return null;
    }
}