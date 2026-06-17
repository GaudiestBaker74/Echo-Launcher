package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModpackManager {

    private static final String USER_AGENT = "ProfessionalMinecraftLauncher/1.0";

    public static Instance importModpack(File file, String curseForgeApiKey) throws Exception {
        if (file == null || !file.exists()) {
            throw new Exception("Invalid modpack file.");
        }

        if (isModrinthPack(file)) {
            return importModrinthPack(file);
        }

        if (isCurseForgePack(file)) {
            return importCurseForgePack(file, curseForgeApiKey);
        }

        throw new Exception("Unrecognized modpack format. Use .mrpack or CurseForge .zip.");
    }

    private static boolean isModrinthPack(File file) {
        if (file.getName().toLowerCase().endsWith(".mrpack")) {
            return true;
        }

        ZipFile zip = null;

        try {
            zip = new ZipFile(file);
            return zip.getEntry("modrinth.index.json") != null;
        } catch (Exception ignored) {
            return false;
        } finally {
            closeZip(zip);
        }
    }

    private static boolean isCurseForgePack(File file) {
        ZipFile zip = null;

        try {
            zip = new ZipFile(file);
            return zip.getEntry("manifest.json") != null;
        } catch (Exception ignored) {
            return false;
        } finally {
            closeZip(zip);
        }
    }

    private static Instance importModrinthPack(File packFile) throws Exception {
        ZipFile zip = new ZipFile(packFile);

        try {
            ZipEntry indexEntry = zip.getEntry("modrinth.index.json");

            if (indexEntry == null) {
                throw new Exception("modrinth.index.json not found.");
            }

            JsonObject index = JsonParser.parseReader(
                    new java.io.InputStreamReader(zip.getInputStream(indexEntry), StandardCharsets.UTF_8)
            ).getAsJsonObject();

            String name = getString(index, "name", cleanName(packFile.getName()));
            JsonObject dependencies = index.has("dependencies") && index.get("dependencies").isJsonObject()
                    ? index.getAsJsonObject("dependencies")
                    : new JsonObject();

            String minecraftVersion = getString(dependencies, "minecraft", "");
            String fabricLoader = getString(dependencies, "fabric-loader", "");

            if (minecraftVersion.trim().isEmpty()) {
                throw new Exception("The modpack does not specify a Minecraft version.");
            }

            String instanceName = makeUniqueInstanceName(name);

            Instance instance = InstanceManager.createInstance(
                    instanceName,
                    "Steve",
                    minecraftVersion,
                    fabricLoader.isEmpty() ? "vanilla" : "fabric",
                    4
            );

            InstanceManager.ensureInstanceFolders(instance);

            if (!fabricLoader.trim().isEmpty()) {
                installFabricSync(instance, minecraftVersion, fabricLoader);
            }

            File gameDir = InstanceManager.getGameDir(instance);

            if (index.has("files") && index.get("files").isJsonArray()) {
                JsonArray files = index.getAsJsonArray("files");

                for (JsonElement e : files) {
                    JsonObject fileObj = e.getAsJsonObject();

                    String path = getString(fileObj, "path", "");

                    if (path.trim().isEmpty()) {
                        continue;
                    }

                    if (!fileObj.has("downloads") || !fileObj.get("downloads").isJsonArray()) {
                        continue;
                    }

                    JsonArray downloads = fileObj.getAsJsonArray("downloads");

                    if (downloads.size() == 0) {
                        continue;
                    }

                    String url = downloads.get(0).getAsString();

                    File target = safeResolve(gameDir, path);

                    System.out.println("[Modpack] Downloading " + path);
                    downloadFile(url, target);
                }
            }

            extractOverrides(zip, "overrides/", gameDir);

            InstanceManager.saveInstance(instance);

            return instance;
        } finally {
            closeZip(zip);
        }
    }

    private static Instance importCurseForgePack(File packFile, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("To import CurseForge modpacks you need to configure the API Key.");
        }

        ZipFile zip = new ZipFile(packFile);

        try {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");

            if (manifestEntry == null) {
                throw new Exception("manifest.json not found.");
            }

            JsonObject manifest = JsonParser.parseReader(
                    new java.io.InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8)
            ).getAsJsonObject();

            String name = getString(manifest, "name", cleanName(packFile.getName()));

            JsonObject minecraft = manifest.getAsJsonObject("minecraft");
            String minecraftVersion = getString(minecraft, "version", "");

            if (minecraftVersion.trim().isEmpty()) {
                throw new Exception("The modpack does not specify a Minecraft version.");
            }

            String loaderId = "";
            String loaderType = "vanilla";

            if (minecraft.has("modLoaders") && minecraft.get("modLoaders").isJsonArray()) {
                JsonArray loaders = minecraft.getAsJsonArray("modLoaders");

                for (JsonElement e : loaders) {
                    JsonObject loader = e.getAsJsonObject();

                    boolean primary = loader.has("primary") && loader.get("primary").getAsBoolean();
                    String id = getString(loader, "id", "");

                    if (primary || loaderId.isEmpty()) {
                        loaderId = id;
                    }
                }
            }

            String instanceName = makeUniqueInstanceName(name);

            Instance instance = InstanceManager.createInstance(
                    instanceName,
                    "Steve",
                    minecraftVersion,
                    loaderId.toLowerCase().startsWith("fabric") ? "fabric" : loaderType,
                    4
            );

            InstanceManager.ensureInstanceFolders(instance);

            if (loaderId.toLowerCase().startsWith("fabric")) {
                String fabricVersion = loaderId.substring("fabric-".length());
                installFabricSync(instance, minecraftVersion, fabricVersion);
            }

            File gameDir = InstanceManager.getGameDir(instance);
            File modsDir = new File(gameDir, "mods");
            modsDir.mkdirs();

            if (manifest.has("files") && manifest.get("files").isJsonArray()) {
                JsonArray files = manifest.getAsJsonArray("files");

                for (JsonElement e : files) {
                    JsonObject f = e.getAsJsonObject();

                    String projectId = getString(f, "projectID", "");
                    String fileId = getString(f, "fileID", "");
                    boolean required = !f.has("required") || f.get("required").getAsBoolean();

                    if (!required || projectId.isEmpty() || fileId.isEmpty()) {
                        continue;
                    }

                    String downloadUrl = CurseForgeClient.getFileDownloadUrl(apiKey, projectId, fileId);

                    if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                        System.err.println("[Modpack] CurseForge file has no download URL: " + projectId + "/" + fileId);
                        continue;
                    }

                    String fileName = getFileNameFromUrl(downloadUrl);

                    if (fileName.trim().isEmpty()) {
                        fileName = projectId + "-" + fileId + ".jar";
                    }

                    File target = new File(modsDir, sanitizeFileName(fileName));

                    System.out.println("[Modpack] Downloading CurseForge file " + projectId + "/" + fileId);
                    downloadFile(downloadUrl, target);
                }
            }

            String overrides = getString(manifest, "overrides", "overrides");
            extractOverrides(zip, overrides + "/", gameDir);

            InstanceManager.saveInstance(instance);

            return instance;
        } finally {
            closeZip(zip);
        }
    }

    private static void installFabricSync(final Instance instance,
                                          final String minecraftVersion,
                                          final String loaderVersion) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> installedId = new AtomicReference<String>();
        final AtomicReference<Exception> error = new AtomicReference<Exception>();

        FabricManager.install(
                new VersionEntry(minecraftVersion),
                loaderVersion,
                new FabricManager.LauncherCallback() {
                    @Override
                    public void onStatus(String status) {
                        System.out.println("[Modpack/Fabric] " + status);
                    }

                    @Override
                    public void onSuccess(String installedVersionId) {
                        installedId.set(installedVersionId);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String err) {
                        error.set(new Exception(err));
                        latch.countDown();
                    }
                }
        );

        latch.await();

        if (error.get() != null) {
            throw error.get();
        }

        if (installedId.get() != null && !installedId.get().trim().isEmpty()) {
            instance.version = installedId.get();
            instance.type = "fabric";
            InstanceManager.saveInstance(instance);
        }
    }

    private static void extractOverrides(ZipFile zip, String prefix, File gameDir) throws Exception {
        if (prefix == null || prefix.trim().isEmpty()) {
            return;
        }

        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            String name = entry.getName().replace("\\", "/");

            if (!name.startsWith(prefix)) {
                continue;
            }

            String relative = name.substring(prefix.length());

            if (relative.trim().isEmpty()) {
                continue;
            }

            File out = safeResolve(gameDir, relative);

            if (entry.isDirectory()) {
                out.mkdirs();
                continue;
            }

            File parent = out.getParentFile();

            if (parent != null) {
                parent.mkdirs();
            }

            InputStream in = zip.getInputStream(entry);

            try {
                FileOutputStream fos = new FileOutputStream(out);

                try {
                    byte[] buffer = new byte[8192];
                    int read;

                    while ((read = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                } finally {
                    fos.close();
                }
            } finally {
                in.close();
            }
        }
    }

    private static void downloadFile(String urlStr, File target) throws Exception {
        File parent = target.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        }

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;

        File tmp = new File(target.getAbsolutePath() + ".tmp");

        try {
            java.net.URL url = URI.create(urlStr).toURL();

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " downloading " + urlStr);
            }

            in = conn.getInputStream();
            out = new FileOutputStream(tmp);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            out.close();
            out = null;

            if (target.exists()) {
                target.delete();
            }

            if (!tmp.renameTo(target)) {
                throw new Exception("Could not move file to " + target.getAbsolutePath());
            }
        } finally {
            if (out != null) {
                out.close();
            }

            if (in != null) {
                in.close();
            }

            if (conn != null) {
                conn.disconnect();
            }

            if (tmp.exists() && (!target.exists() || target.length() == 0)) {
                tmp.delete();
            }
        }
    }

    private static File safeResolve(File baseDir, String relativePath) throws Exception {
        File target = new File(baseDir, relativePath);

        String base = baseDir.getCanonicalPath();
        String out = target.getCanonicalPath();

        if (!out.startsWith(base + File.separator)) {
            throw new Exception("Unsafe path in modpack: " + relativePath);
        }

        return target;
    }

    private static String makeUniqueInstanceName(String preferred) {
        if (preferred == null || preferred.trim().isEmpty()) {
            preferred = "Imported Modpack";
        }

        String base = preferred.trim();
        String name = base;
        int index = 2;

        while (new File(InstanceManager.getBaseDir(), InstanceManager.safeName(name)).exists()) {
            name = base + " " + index;
            index++;
        }

        return name;
    }

    private static String cleanName(String fileName) {
        if (fileName == null) {
            return "Imported Modpack";
        }

        String name = fileName;

        if (name.toLowerCase().endsWith(".mrpack")) {
            name = name.substring(0, name.length() - ".mrpack".length());
        }

        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - ".zip".length());
        }

        name = name.replace("_", " ").replace("-", " ").trim();

        return name.isEmpty() ? "Imported Modpack" : name;
    }

    private static String getFileNameFromUrl(String url) {
        try {
            String clean = url;

            int q = clean.indexOf("?");

            if (q >= 0) {
                clean = clean.substring(0, q);
            }

            int slash = clean.lastIndexOf("/");

            if (slash >= 0) {
                clean = clean.substring(slash + 1);
            }

            return URLDecoder.decode(clean, "UTF-8");
        } catch (Exception ex) {
            return "";
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "downloaded-file.jar";
        }

        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static void closeZip(ZipFile zip) {
        try {
            if (zip != null) {
                zip.close();
            }
        } catch (Exception ignored) {
        }
    }
}