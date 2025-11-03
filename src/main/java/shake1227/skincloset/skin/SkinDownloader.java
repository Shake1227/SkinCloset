package shake1227.skincloset.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SkinDownloader {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String MOJANG_UUID_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MOJANG_SKIN_API = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String MINESKIN_API = "https://api.mineskin.org/generate/upload";


    // エラー 14 の修正
    public static void fetchSkinByUsername(String username, Consumer<SkinProfile> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Username から UUID を取得
                String uuidJson = fetch(MOJANG_UUID_API + username);
                if (uuidJson == null) {
                    callback.accept(null);
                    return;
                }
                JsonObject uuidObj = JsonParser.parseString(uuidJson).getAsJsonObject();
                String uuidStr = uuidObj.get("id").getAsString();
                String actualName = uuidObj.get("name").getAsString(); // 取得した正式な名前
                UUID uuid = UUID.fromString(uuidStr.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5"
                ));

                // 2. UUID から Skin 情報を取得
                String skinJson = fetch(MOJANG_SKIN_API + uuidStr + "?unsigned=false");
                if (skinJson == null) {
                    callback.accept(null);
                    return;
                }
                JsonObject skinObj = JsonParser.parseString(skinJson).getAsJsonObject();
                JsonObject textureProp = skinObj.getAsJsonArray("properties").get(0).getAsJsonObject();

                String value = textureProp.get("value").getAsString();
                String signature = textureProp.get("signature").getAsString();

                callback.accept(new SkinProfile(actualName, uuid, value, signature));

            } catch (Exception e) {
                e.printStackTrace();
                callback.accept(null);
            }
        });
    }

    // エラー 16 の修正
    public static void uploadSkinFromLocal(String profileName, Path filePath, Consumer<SkinProfile> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                String boundary = "===" + System.currentTimeMillis() + "===";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(MINESKIN_API + "?visibility=1")) // private
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(ofMimeMultipartData(filePath, boundary, profileName))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonObj = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonObject data = jsonObj.getAsJsonObject("data");
                    JsonObject texture = data.getAsJsonObject("texture");

                    String value = texture.get("value").getAsString();
                    String signature = texture.get("signature").getAsString();
                    UUID uuid = UUID.fromString(data.get("uuid").getAsString());

                    callback.accept(new SkinProfile(profileName, uuid, value, signature));
                } else {
                    callback.accept(null);
                }

            } catch (Exception e) {
                e.printStackTrace();
                callback.accept(null);
            }
        });
    }

    // エラー 18 の修正
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


    // --- Helper Methods ---

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
        String body = "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + CRLF +
                "Content-Type: image/png" + CRLF + CRLF;

        byte[] header = body.getBytes();
        byte[] footer = (CRLF + "--" + boundary + "--" + CRLF).getBytes();

        return HttpRequest.BodyPublishers.ofByteArrays(List.of(header, fileBytes, footer));
    }
}

