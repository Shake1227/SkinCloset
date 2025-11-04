package shake1227.skincloset.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import shake1227.skincloset.SkinCloset;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class LauncherSkinImporter {
    private record LauncherSkinInfo(String name, String url, String model) {}

    public static void importSkins(Consumer<String> statusCallback) {
        SkinCloset.LOGGER.info("Starting launcher skin import...");

        try {
            Path mcDir = Path.of(Minecraft.DEFAULT_FONT.getPath());
            Path launcherSkinsFile = mcDir.resolve("launcher_skins.json");
            if (!Files.exists(launcherSkinsFile)) {
                SkinCloset.LOGGER.warn("launcher_skins.json not found. Trying launcher_custom_skins.json...");
                launcherSkinsFile = mcDir.resolve("launcher_custom_skins.json");
            }
            if (!Files.exists(launcherSkinsFile)) {
                SkinCloset.LOGGER.warn("Not found in default dir. Checking current gameDir (dev env)...");
                mcDir = Minecraft.getInstance().gameDirectory.toPath();
                launcherSkinsFile = mcDir.resolve("launcher_custom_skins.json");
            }

            if (!Files.exists(launcherSkinsFile)) {
                SkinCloset.LOGGER.warn("No launcher skin files found in default OR current directory.");
                statusCallback.accept("gui.skincloset.import_failed");
                return;
            }

            SkinCloset.LOGGER.info("Reading launcher skins from: {}", launcherSkinsFile.toAbsolutePath());
            List<LauncherSkinInfo> skinsToImport = new ArrayList<>();
            try (Reader reader = Files.newBufferedReader(launcherSkinsFile, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray skinsArray = new JsonArray();
                if (json.has("customSkins") && json.get("customSkins").isJsonObject()) {
                    JsonObject customSkins = json.getAsJsonObject("customSkins");
                    for (String key : customSkins.keySet()) {
                        skinsArray.add(customSkins.get(key));
                    }
                } else if (json.has("skins") && json.get("skins").isJsonArray()) {
                    skinsArray = json.getAsJsonArray("skins");
                }

                if (skinsArray.size() > 0) {
                    for (JsonElement skinElement : skinsArray) {
                        JsonObject skinObj = skinElement.getAsJsonObject();
                        String url = "";
                        if (skinObj.has("url")) {
                            url = skinObj.get("url").getAsString();
                        } else if (skinObj.has("skinImage")) {
                            url = skinObj.get("skinImage").getAsString();
                        }

                        if (!url.isEmpty() && skinObj.has("name")) {
                            String name = skinObj.get("name").getAsString();
                            String model = "classic";
                            if ((skinObj.has("variant") && skinObj.get("variant").getAsString().equalsIgnoreCase("slim")) ||
                                    (skinObj.has("slim") && skinObj.get("slim").getAsBoolean())) {
                                model = "slim";
                            }
                            skinsToImport.add(new LauncherSkinInfo(name, url, model));
                        }
                    }
                }
            }

            if (skinsToImport.isEmpty()) {
                SkinCloset.LOGGER.info("No valid skins found in launcher file.");
                statusCallback.accept("gui.skincloset.import_nothing_new");
                return;
            }

            SkinCloset.LOGGER.info("Found {} skins in launcher. Starting import...", skinsToImport.size());
            AtomicInteger successCount = new AtomicInteger(0);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (LauncherSkinInfo info : skinsToImport) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                futures.add(future);
                SkinDownloader.uploadSkinFromUrl(info.name(), info.url(), (newProfile) -> {
                    if (newProfile != null) {
                        newProfile.setModel(info.model());
                        SkinCache.addProfile(newProfile);
                        successCount.incrementAndGet();
                    }
                    future.complete(null);
                });
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                if (successCount.get() > 0) {
                    SkinCache.saveProfiles();
                    SkinCloset.LOGGER.info("Import complete. Added {} new skins.", successCount.get());
                    statusCallback.accept("gui.skincloset.import_success:" + successCount.get());
                } else {
                    SkinCloset.LOGGER.info("Import complete. No new skins were added (all duplicates).");
                    statusCallback.accept("gui.skincloset.import_nothing_new");
                }
            });

        } catch (Exception e) {
            SkinCloset.LOGGER.error("Failed to read or parse launcher_skins.json", e);
            statusCallback.accept("gui.skincloset.import_failed");
        }
    }
}