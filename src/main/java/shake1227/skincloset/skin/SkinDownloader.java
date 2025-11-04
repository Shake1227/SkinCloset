package shake1227.skincloset.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import shake1227.skincloset.Constants;
import shake1227.skincloset.SkinCloset;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SkinDownloader {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static void fetchSkinByUsername(String username, Consumer<SkinProfile> callback) {
        SkinCloset.LOGGER.info("Fetching skin for username: {}", username);
        CompletableFuture.runAsync(() -> {
            try {
                String uuidJson = fetch(Constants.MOJANG_UUID_API + username);
                if (uuidJson == null) {
                    SkinCloset.LOGGER.error("Failed to fetch UUID for {}. (Mojang API returned null)", username);
                    callback.accept(null);
                    return;
                }
                JsonObject uuidObj = JsonParser.parseString(uuidJson).getAsJsonObject();
                String uuidStr = uuidObj.get("id").getAsString();
                String actualName = uuidObj.get("name").getAsString();
                UUID uuid = UUID.fromString(uuidStr.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5"
                ));
                String skinJson = fetch(Constants.MOJANG_SKIN_API + uuidStr + "?unsigned=false");
                if (skinJson == null) {
                    SkinCloset.LOGGER.error("Failed to fetch Skin data for {} ({}). (Mojang API returned null)", actualName, uuidStr);
                    callback.accept(null);
                    return;
                }
                JsonObject skinObj = JsonParser.parseString(skinJson).getAsJsonObject();
                JsonObject textureProp = skinObj.getAsJsonArray("properties").get(0).getAsJsonObject();

                String value = textureProp.get("value").getAsString();
                String signature = textureProp.get("signature").getAsString();
                String model = "classic";
                try {
                    String decodedValue = new String(Base64.getDecoder().decode(value));
                    JsonObject textureJson = JsonParser.parseString(decodedValue).getAsJsonObject();
                    if (textureJson.getAsJsonObject("textures").getAsJsonObject("SKIN").has("metadata")) {
                        model = textureJson.getAsJsonObject("textures").getAsJsonObject("SKIN").getAsJsonObject("metadata").get("model").getAsString();
                    }
                } catch (Exception e) {
                    SkinCloset.LOGGER.warn("Could not determine skin model for {}, defaulting to classic.", actualName);
                }

                SkinCloset.LOGGER.info("Successfully fetched skin for {} (Model: {})", actualName, model);
                callback.accept(new SkinProfile(actualName, uuid, value, signature, model));

            } catch (Exception e) {
                SkinCloset.LOGGER.error("Exception while fetching skin by username: ", e);
                callback.accept(null);
            }
        });
    }
    public static void uploadSkinFromLocal(String profileName, Path filePath, Consumer<SkinProfile> callback) {
        SkinCloset.LOGGER.info("Uploading skin from local file: {} as {}", filePath, profileName);
        CompletableFuture.runAsync(() -> {
            try {
                String boundary = "Boundary-" + System.currentTimeMillis();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(Constants.MINESKIN_API_UPLOAD + "?visibility=1"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(ofMimeMultipartData(filePath, boundary, profileName))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    SkinCloset.LOGGER.info("Mineskin API (upload) success (200)");
                    JsonObject jsonObj = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonObject data = jsonObj.getAsJsonObject("data");
                    JsonObject texture = data.getAsJsonObject("texture");

                    String value = texture.get("value").getAsString();
                    String signature = texture.get("signature").getAsString();
                    UUID uuid = UUID.fromString(data.get("uuid").getAsString());
                    String model = "classic";
                    if (data.has("variant") && !data.get("variant").isJsonNull()) {
                        model = data.get("variant").getAsString();
                    }
                    callback.accept(new SkinProfile(profileName, uuid, value, signature, model));
                } else {
                    SkinCloset.LOGGER.error("Mineskin API (upload) Error: {} \nResponse: {}", response.statusCode(), response.body());
                    callback.accept(null);
                }

            } catch (Exception e) {
                SkinCloset.LOGGER.error("Exception while uploading skin from local: ", e);
                callback.accept(null);
            }
        });
    }
    public static void uploadSkinFromUrl(String profileName, String skinUrl, Consumer<SkinProfile> callback) {
        SkinCloset.LOGGER.info("Uploading skin from URL for {}: {}", profileName, skinUrl);
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("url", skinUrl);
                payload.addProperty("name", profileName);
                payload.addProperty("visibility", 1);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(Constants.MINESKIN_API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    SkinCloset.LOGGER.info("Mineskin API (url) success (200)");
                    JsonObject jsonObj = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonObject data = jsonObj.getAsJsonObject("data");
                    JsonObject texture = data.getAsJsonObject("texture");

                    String value = texture.get("value").getAsString();
                    String signature = texture.get("signature").getAsString();
                    UUID uuid = UUID.fromString(data.get("uuid").getAsString());
                    String model = "classic";
                    if (data.has("variant") && !data.get("variant").isJsonNull()) {
                        model = data.get("variant").getAsString();
                    }
                    callback.accept(new SkinProfile(profileName, uuid, value, signature, model));
                } else {
                    SkinCloset.LOGGER.error("Mineskin API (url) Error: {} \nResponse: {}", response.statusCode(), response.body());
                    callback.accept(null);
                }
            } catch (Exception e) {
                SkinCloset.LOGGER.error("Exception while uploading skin from URL: ", e);
                callback.accept(null);
            }
        });
    }
    public static void downloadSkinTexture(String url, Path targetPath, Consumer<Boolean> callback) {
        CompletableFuture.runAsync(() -> {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                callback.accept(true);
            } catch (IOException e) {
                e.printStackTrace();
                callback.accept(false);
            }
        });
    }


    private static String fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    private static HttpRequest.BodyPublisher ofMimeMultipartData(Path filePath, String boundary, String profileName) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        String fileName = filePath.getFileName().toString();
        String CRLF = "\r\n";
        String part1 = "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"name\"" + CRLF + CRLF +
                profileName + CRLF;
        String part2Header = "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + CRLF +
                "Content-Type: image/png" + CRLF + CRLF;
        String footer = CRLF + "--" + boundary + "--" + CRLF;
        return HttpRequest.BodyPublishers.ofByteArrays(List.of(
                part1.getBytes(),
                part2Header.getBytes(),
                fileBytes,
                footer.getBytes()
        ));
    }
}