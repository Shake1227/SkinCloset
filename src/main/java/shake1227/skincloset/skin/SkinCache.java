package shake1227.skincloset.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkinCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("skincloset");
    private static final Path CACHE_FILE = CONFIG_DIR.resolve("skins.json");


    private static final List<SkinProfile> profiles = new ArrayList<>();

    public static void loadProfiles() {
        try {
            Files.createDirectories(CONFIG_DIR);

            if (Files.exists(CACHE_FILE)) {
                try (Reader reader = Files.newBufferedReader(CACHE_FILE, StandardCharsets.UTF_8)) {
                    Type listType = new TypeToken<ArrayList<SkinProfile>>() {}.getType();
                    List<SkinProfile> loaded = GSON.fromJson(reader, listType);
                    if (loaded != null) {
                        profiles.clear();
                        profiles.addAll(loaded);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveProfiles() {
        try (Writer writer = Files.newBufferedWriter(CACHE_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(profiles, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<SkinProfile> getProfiles() {
        return profiles;
    }

    public static void addProfile(SkinProfile profile) {
        if (profiles.stream().noneMatch(p ->
                p.getUuid().equals(profile.getUuid()) &&
                        p.getName().equals(profile.getName()) &&
                        p.getSkinData().map(sd -> sd.signature()).equals(profile.getSkinData().map(sd -> sd.signature()))
        )) {
            profiles.add(profile);
        }
    }

    public static void removeProfile(SkinProfile profile) {
        profiles.remove(profile);
    }
}