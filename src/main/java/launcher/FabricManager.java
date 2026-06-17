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
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FabricManager {

    private static final String META_URL = "https://meta.fabricmc.net/v2";
    private static final String USER_AGENT = "ProfessionalMinecraftLauncher/1.0";

    public static class FabricLoaderVersion {
        public String version;
        public boolean stable;

        public FabricLoaderVersion(String version, boolean stable) {
            this.version = version;
            this.stable = stable;
        }

        @Override
        public String toString() {
            return version + (stable ? "  Stable" : "  Beta");
        }
    }

    public static List<FabricLoaderVersion> getLoaderVersions(String minecraftVersion) throws Exception {
        String mcVersion = extractMinecraftVersion(minecraftVersion);

        String loadersUrl = META_URL + "/versions/loader/" + mcVersion;
        String loadersJson = readUrl(loadersUrl);

        JsonArray loaders = JsonParser.parseString(loadersJson).getAsJsonArray();

        List<FabricLoaderVersion> result = new ArrayList<FabricLoaderVersion>();

        for (int i = 0; i < loaders.size(); i++) {
            JsonObject obj = loaders.get(i).getAsJsonObject();

            if (!obj.has("loader")) {
                continue;
            }

            JsonObject loader = obj.getAsJsonObject("loader");

            String version = loader.has("version") ? loader.get("version").getAsString() : "";
            boolean stable = loader.has("stable") && loader.get("stable").getAsBoolean();

            if (!version.trim().isEmpty()) {
                result.add(new FabricLoaderVersion(version, stable));
            }
        }

        return result;
    }

    public static void install(final VersionEntry version,
            final String loaderVersion,
            final LauncherCallback callback) {
        if (callback == null) {
            return;
        }

        if (version == null || version.id == null || version.id.trim().isEmpty()) {
            callback.onError("Invalid version");
            return;
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    installSync(version, loaderVersion, callback);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError("Error installing Fabric: " + e.getMessage());
                }
            }
        }, "Fabric-Installer");

        t.setDaemon(true);
        t.start();
    }

    public static void install(final VersionEntry version, final LauncherCallback callback) {
        install(version, null, callback);
    }

    private static void installSync(VersionEntry version, String selectedLoaderVersion, LauncherCallback callback)
            throws Exception {
        String mcVersion = extractMinecraftVersion(version.id);

        callback.onStatus("Searching for Fabric Loader for Minecraft " + mcVersion + "...");

        String loaderVersion = selectedLoaderVersion;

        if (loaderVersion == null || loaderVersion.trim().isEmpty()) {
            List<FabricLoaderVersion> versions = getLoaderVersions(mcVersion);

            if (versions.isEmpty()) {
                callback.onError("Fabric Loader for Minecraft was not found: " + mcVersion);
                return;
            }

            FabricLoaderVersion selected = null;

            for (FabricLoaderVersion v : versions) {
                if (v.stable) {
                    selected = v;
                    break;
                }
            }

            if (selected == null) {
                selected = versions.get(0);
            }

            loaderVersion = selected.version;
        }

        callback.onStatus("Installing Fabric Loader " + loaderVersion + " for Minecraft " + mcVersion + "...");

        String profileUrl = META_URL + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        String profileJson = readUrl(profileUrl);

        JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();

        if (!profile.has("id")) {
            throw new Exception("Fabric profile does not contain ID.");
        }

        String profileId = profile.get("id").getAsString();

        File versionDir = new File(VersionManager.MC_DIR, "versions/" + profileId);

        if (!versionDir.exists() && !versionDir.mkdirs()) {
            throw new Exception("Could not create directory: " + versionDir.getAbsolutePath());
        }

        File jsonFile = new File(versionDir, profileId + ".json");
        writeFile(jsonFile, profileJson);

        callback.onStatus("Fabric Profile saved: " + profileId);
        callback.onSuccess(profileId);
    }

    private static String extractMinecraftVersion(String versionId) {
        if (versionId == null) {
            return "";
        }

        versionId = versionId.trim();

        if (versionId.matches("\\d+\\.\\d+(\\.\\d+)?")) {
            return versionId;
        }

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
            URL url = URI.create(urlStr).toURL();
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
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();

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
            throw new Exception("The folder could not be created: " + parent.getAbsolutePath());
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