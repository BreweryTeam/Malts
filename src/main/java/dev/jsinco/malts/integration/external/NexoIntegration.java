package dev.jsinco.malts.integration.external;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jsinco.malts.Malts;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.integration.Integration;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Adds item definitions to Nexo's resource pack that render borrowed item placeholders
// as their vanilla model with a semi-transparent gray square on top
public class NexoIntegration implements Integration {

    private static final String NAMESPACE = "malts";
    private static final String OVERLAY = NAMESPACE + ":item/borrowed_overlay";
    private static final int OVERLAY_COLOR = 0xA0707070; // ARGB
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ITEMS_PREFIX = "assets/minecraft/items/";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private volatile Set<String> fadedItems = Set.of();

    @Override
    public boolean canRegister() {
        return Bukkit.getPluginManager().isPluginEnabled("Nexo") && ConfigManager.get(Config.class).vaults().lockingEnabled();
    }

    @Override
    public void register() {
        String version = Bukkit.getMinecraftVersion();
        Map<String, JsonElement> cached = readCache(version);
        if (cached != null) {
            apply(cached);
            return;
        }

        Executors.runAsync(task -> {
            try {
                Text.log("Downloading the Minecraft " + version + " client to build borrowed item models for Nexo...");
                Map<String, JsonElement> models = downloadModels(version);
                writeCache(version, models);
                apply(models);
            } catch (IOException | InterruptedException | RuntimeException e) {
                Text.error("Failed to build borrowed item models for Nexo", e);
            }
        });
    }

    @Override
    public String name() {
        return "Nexo";
    }

    @Nullable
    public NamespacedKey fadedModel(Material material) {
        String key = material.getKey().getKey();
        return fadedItems.contains(key) ? new NamespacedKey(NAMESPACE, "borrowed/" + key) : null;
    }

    private void apply(Map<String, JsonElement> models) {
        fadedItems = Set.copyOf(models.keySet());
        if (writePackAssets(nexoFolder(), models)) {
            Text.log("Added borrowed item models to Nexo's resource pack. Run '/nexo reload pack' to apply them.");
        }
    }

    // Called on load so the assets exist before Nexo generates its pack
    public static void writePackAssetsIfPresent() {
        Path folder = nexoFolder();
        Map<String, JsonElement> cached = readCache(Bukkit.getMinecraftVersion());
        if (Files.isDirectory(folder) && cached != null) {
            writePackAssets(folder, cached);
        }
    }

    private static Path nexoFolder() {
        return Malts.getInstance().getDataFolder().toPath().getParent().resolve("Nexo");
    }

    private static Path cacheFile(String version) {
        return Malts.getInstance().getDataFolder().toPath().resolve("cache").resolve("vanilla_item_models-" + version + ".json");
    }

    @Nullable
    private static Map<String, JsonElement> readCache(String version) {
        Path file = cacheFile(version);
        if (!Files.exists(file)) return null;
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject().asMap();
        } catch (IOException | RuntimeException e) {
            Text.error("Failed to read cached vanilla item models, they will be downloaded again", e);
            return null;
        }
    }

    private static void writeCache(String version, Map<String, JsonElement> models) throws IOException {
        JsonObject json = new JsonObject();
        models.forEach(json::add);
        Path file = cacheFile(version);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json.toString());
    }

    // Falls back to the newest version if the server's version isn't in the manifest
    private static Map<String, JsonElement> downloadModels(String version) throws IOException, InterruptedException {
        JsonArray versions = getJson(VERSION_MANIFEST).getAsJsonArray("versions");
        JsonObject entry = null;
        for (JsonElement element : versions) {
            if (version.equals(element.getAsJsonObject().get("id").getAsString())) {
                entry = element.getAsJsonObject();
                break;
            }
        }
        if (entry == null) {
            entry = versions.get(0).getAsJsonObject();
            Text.warn("Minecraft " + version + " not found in Mojang's version manifest, using " + entry.get("id").getAsString() + " instead.");
        }

        JsonObject client = getJson(entry.get("url").getAsString()).getAsJsonObject("downloads").getAsJsonObject("client");
        Path jar = Files.createTempFile("malts-client", ".jar");
        try {
            HTTP.send(HttpRequest.newBuilder(URI.create(client.get("url").getAsString())).build(), HttpResponse.BodyHandlers.ofFile(jar));
            if (!sha1(jar).equalsIgnoreCase(client.get("sha1").getAsString())) {
                throw new IOException("Client jar checksum mismatch");
            }

            Map<String, JsonElement> models = new TreeMap<>();
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry zipEntry : zip.stream().toList()) {
                    String name = zipEntry.getName();
                    if (!name.startsWith(ITEMS_PREFIX) || !name.endsWith(".json") || name.indexOf('/', ITEMS_PREFIX.length()) != -1) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(zipEntry)) {
                        JsonElement model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().get("model");
                        models.put(name.substring(ITEMS_PREFIX.length(), name.length() - ".json".length()), model);
                    }
                }
            }
            return models;
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Request to " + url + " failed with status " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean writePackAssets(Path nexoFolder, Map<String, JsonElement> models) {
        Path assets = nexoFolder.resolve("pack").resolve("assets").resolve(NAMESPACE);
        boolean changed = false;
        try {
            changed |= write(assets.resolve("textures/item/borrowed_overlay.png"), overlayTexture());
            changed |= write(assets.resolve("models/item/borrowed_overlay.json"), overlayModel().toString().getBytes(StandardCharsets.UTF_8));

            for (Map.Entry<String, JsonElement> entry : models.entrySet()) {
                JsonObject overlay = new JsonObject();
                overlay.addProperty("type", "minecraft:model");
                overlay.addProperty("model", OVERLAY);

                JsonArray parts = new JsonArray();
                parts.add(entry.getValue());
                parts.add(overlay);

                JsonObject composite = new JsonObject();
                composite.addProperty("type", "minecraft:composite");
                composite.add("models", parts);

                JsonObject definition = new JsonObject();
                definition.add("model", composite);
                changed |= write(assets.resolve("items/borrowed/" + entry.getKey() + ".json"), definition.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Text.error("Failed to write borrowed item models to Nexo's pack", e);
        }
        return changed;
    }

    private static boolean write(Path path, byte[] data) throws IOException {
        if (Files.exists(path) && Arrays.equals(Files.readAllBytes(path), data)) {
            return false;
        }
        Files.createDirectories(path.getParent());
        Files.write(path, data);
        return true;
    }

    private static byte[] overlayTexture() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                image.setRGB(x, y, OVERLAY_COLOR);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // A flat square in front of the item, pushed slightly towards the camera
    private static JsonObject overlayModel() {
        return JsonParser.parseString("""
                {
                  "textures": {"overlay": "%1$s", "particle": "%1$s"},
                  "gui_light": "front",
                  "elements": [{
                    "from": [0, 0, 16], "to": [16, 16, 16],
                    "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#overlay"}}
                  }],
                  "display": {"gui": {"translation": [0, 0, 2]}}
                }
                """.formatted(OVERLAY)).getAsJsonObject();
    }
}
