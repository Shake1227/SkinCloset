package shake1227.skincloset.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkinCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("skincloset");
    private static final Path CACHE_FILE = CONFIG_DIR.resolve("skins.json");

    // --- エラー修正のため public static final に変更 ---
    public static final Path UPLOADS_DIR = CONFIG_DIR.resolve("uploads");

    private static final List<SkinProfile> profiles = new ArrayList<>();

    public static void loadProfiles() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(UPLOADS_DIR); // uploadsフォルダも作成

            if (Files.exists(CACHE_FILE)) {
                try (FileReader reader = new FileReader(CACHE_FILE.toFile())) {
                    Type listType = new TypeToken<ArrayList<SkinProfile>>() {}.getType();
                    List<SkinProfile> loaded = GSON.fromJson(reader, listType);
                    if (loaded != null) {
                        profiles.addAll(loaded);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveProfiles() {
        try (FileWriter writer = new FileWriter(CACHE_FILE.toFile())) {
            GSON.toJson(profiles, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<SkinProfile> getProfiles() {
        return profiles;
    }

    public static void addProfile(SkinProfile profile) {
        // 重複チェック
        if (profiles.stream().noneMatch(p -> p.getUuid().equals(profile.getUuid()) && p.getName().equals(profile.getName()))) {
            profiles.add(profile);
        }
    }

    public static void removeProfile(SkinProfile profile) {
        profiles.remove(profile);
    }
}

