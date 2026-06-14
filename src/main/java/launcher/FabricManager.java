package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FabricManager {

    private static final String META_URL = "https://meta.fabricmc.net/v2";
    private static final String USER_AGENT = "ProfessionalMinecraftLauncher/1.0";

    public static void install(final VersionEntry version, final LauncherCallback callback) {
        if (callback == null) {
            return;
        }

        if (version == null || version.id == null || version.id.trim().isEmpty()) {
            callback.onError("Versión inválida.");
            return;
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    installSync(version, callback);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError("Error installing Fabric: " + e.getMessage());
                }
            }
        }, "Fabric-Installer");

        t.setDaemon(true);
        t.start();
    }

    private static void installSync(VersionEntry version, LauncherCallback callback) throws Exception {
        String mcVersion = version.id.trim();

        // Si el usuario selecciona ya una versión Fabric, intentamos extraer la versión vanilla.
        mcVersion = extractMinecraftVersion(mcVersion);

        callback.onStatus("Buscando Fabric Loader para Minecraft " + mcVersion + "...");

        String loadersUrl = META_URL + "/versions/loader/" + mcVersion;
        String loadersJson = readUrl(loadersUrl);

        JsonArray loaders = JsonParser.parseString(loadersJson).getAsJsonArray();

        if (loaders.size() == 0) {
            callback.onError("No se encontró Fabric Loader para Minecraft " + mcVersion);
            return;
        }

        JsonObject selectedLoader = null;

        // Preferir loader estable.
        for (int i = 0; i < loaders.size(); i++) {
            JsonObject obj = loaders.get(i).getAsJsonObject();

            if (obj.has("loader")) {
                JsonObject loader = obj.getAsJsonObject("loader");

                if (loader.has("stable") && loader.get("stable").getAsBoolean()) {
                    selectedLoader = obj;
                    break;
                }
            }
        }

        // Si no hay estable, usar el primero.
        if (selectedLoader == null) {
            selectedLoader = loaders.get(0).getAsJsonObject();
        }

        JsonObject loaderObj = selectedLoader.getAsJsonObject("loader");
        String loaderVersion = loaderObj.get("version").getAsString();

        callback.onStatus("Fabric Loader encontrado: " + loaderVersion);

        String profileUrl = META_URL + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        String profileJson = readUrl(profileUrl);

        JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();

        if (!profile.has("id")) {
            throw new Exception("El perfil de Fabric no contiene ID.");
        }

        String profileId = profile.get("id").getAsString();

        File versionDir = new File(VersionManager.MC_DIR, "versions/" + profileId);

        if (!versionDir.exists() && !versionDir.mkdirs()) {
            throw new Exception("No se pudo crear la carpeta: " + versionDir.getAbsolutePath());
        }

        File jsonFile = new File(versionDir, profileId + ".json");
        writeFile(jsonFile, profileJson);

        callback.onStatus("Perfil Fabric guardado: " + profileId);
        callback.onSuccess(profileId);
    }

    private static String extractMinecraftVersion(String versionId) {
        if (versionId == null) {
            return "";
        }

        // Ejemplo:
        // fabric-loader-0.16.9-1.20.1 -> 1.20.1
        // fabric-loader-0.15.11-1.21 -> 1.21
        String[] parts = versionId.split("-");

        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+\\.\\d+(\\.\\d+)?")) {
                return parts[i];
            }
        }

        return versionId;
    }

    private static String readUrl(String urlStr) throws Exception {
        System.out.println("[Fabric] GET " + urlStr);

        HttpURLConnection conn = null;

        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            InputStream stream;

            if (code >= 200 && code < 300) {
                stream = conn.getInputStream();
            } else {
                stream = conn.getErrorStream();

                String errorBody = "";

                if (stream != null) {
                    errorBody = readStream(stream);
                }

                throw new Exception("HTTP " + code + " al consultar Fabric. " + errorBody);
            }

            return readStream(stream);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        try {
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            reader.close();
        }

        return sb.toString();
    }

    private static void writeFile(File file, String text) throws Exception {
        File parent = file.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("No se pudo crear la carpeta: " + parent.getAbsolutePath());
        }

        FileOutputStream out = new FileOutputStream(file);

        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    public interface LauncherCallback {
        void onStatus(String status);

        void onSuccess(String installedVersionId);

        void onError(String error);
    }
}