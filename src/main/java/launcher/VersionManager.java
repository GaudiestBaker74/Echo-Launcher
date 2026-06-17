package launcher;

import com.google.gson.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class VersionManager {

    public static final File MC_DIR = PlatformManager.getDefaultMinecraftDir();
    private static final Set<String> prepared = new HashSet<String>();

    /* ================== VERSIONES ================== */

    public static List<VersionEntry> getVersions() {
        List<VersionEntry> list = new ArrayList<VersionEntry>();
        Set<String> addedIds = new HashSet<String>();

        // 1. Scan Local Versions (Fabric, Forge, etc.)
        File versionsDir = new File(MC_DIR, "versions");
        if (versionsDir.exists() && versionsDir.isDirectory()) {
            File[] dirs = versionsDir.listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.isDirectory()) {
                        File jsonFile = new File(dir, dir.getName() + ".json");
                        if (jsonFile.exists()) {
                            try {
                                JsonObject json = JsonParser.parseReader(new FileReader(jsonFile)).getAsJsonObject();
                                String id = json.get("id").getAsString();
                                String type = json.has("type") ? json.get("type").getAsString() : "custom";
                                list.add(new VersionEntry(id)); // Add local version
                                addedIds.add(id);
                            } catch (Exception e) {
                                System.err.println(
                                        "Error reading local version " + dir.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }

        // 2. Fetch Remote versions (Vanilla)
        File manifestFile = new File(MC_DIR, "version_manifest.json");
        try {
            String jsonText = null;
            boolean cacheExpired = !manifestFile.exists()
                    || (System.currentTimeMillis() - manifestFile.lastModified() > 3600000); // 1 hour

            if (cacheExpired) {
                String[] urls = {
                    "https://piston-meta.mojang.com/mc/game/version_manifest.json",
                    "https://launchermeta.mojang.com/mc/game/version_manifest.json",
                    "https://meta.minecraftpocket.gg/mc/game/version_manifest.json"
                };
                
                for (String manifestUrl : urls) {
                    try {
                        System.out.println("Trying: " + manifestUrl);
                        jsonText = readUrl(manifestUrl);
                        writeFile(manifestFile, jsonText);
                        System.out.println("Successfully downloaded manifest from: " + manifestUrl);
                        break;
                    } catch (Exception e) {
                        System.err.println("Failed to download from " + manifestUrl + ": " + e.getMessage());
                    }
                }
            }

            if (jsonText == null && manifestFile.exists()) {
                System.out.println("Using cached version manifest.");
                try {
                    jsonText = new String(java.nio.file.Files.readAllBytes(manifestFile.toPath()), "UTF-8");
                    
                    // Verify cached manifest is valid
                    JsonObject testRoot = JsonParser.parseString(jsonText).getAsJsonObject();
                    JsonArray testVersions = testRoot.getAsJsonArray("versions");
                    System.out.println("Cached manifest valid, contains " + testVersions.size() + " versions");
                } catch (Exception e) {
                    System.err.println("Cached manifest corrupted, deleting: " + e.getMessage());
                    manifestFile.delete();
                    jsonText = null;
                }
            }

            if (jsonText != null) {
                JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
                JsonArray versions = root.getAsJsonArray("versions");

                for (JsonElement e : versions) {
                    JsonObject v = e.getAsJsonObject();
                    String id = v.get("id").getAsString();
                    if (!addedIds.contains(id) && "release".equals(v.get("type").getAsString())) {
                        list.add(new VersionEntry(id));
                        addedIds.add(id);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Fallback: if no versions, add some common ones
        if (list.isEmpty()) {
            System.out.println("No versions found, using fallback list");
            String[] fallbackVersions = {
                "1.21.4", "1.20.6", "1.20.4", "1.20.1", "1.19.4", 
                "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9"
            };
            for (String id : fallbackVersions) {
                list.add(new VersionEntry(id));
            }
        }
        
        System.out.println("Total versions loaded: " + list.size());
        return list;
    }

    /* ================== PREPARAR VERSION ================== */

    public static JsonObject prepareVersion(String version) throws Exception {
        if (prepared.contains(version)) {
            File localJson = new File(MC_DIR, "versions/" + version + "/" + version + ".json");
            return JsonParser.parseReader(new FileReader(localJson)).getAsJsonObject();
        }

        File versionDir = new File(MC_DIR, "versions/" + version);
        versionDir.mkdirs();
        File jsonFile = new File(versionDir, version + ".json");

        JsonObject root;
        if (jsonFile.exists()) {
            root = JsonParser.parseReader(new FileReader(jsonFile)).getAsJsonObject();
        } else {
            // If not found locally, try to download from Mojang
            String versionUrl = getVersionUrl(version);
            String jsonText = readUrl(versionUrl);
            writeFile(jsonFile, jsonText);
            root = JsonParser.parseString(jsonText).getAsJsonObject();
        }

        // 1. Handle Inheritance (Fabric/Forge)
        if (root.has("inheritsFrom")) {
            String parent = root.get("inheritsFrom").getAsString();
            System.out.println(version + " inherits from " + parent);
            prepareVersion(parent);
        }

        if (root.has("downloads") && root.getAsJsonObject("downloads").has("client")) {
            JsonObject client = root.getAsJsonObject("downloads").getAsJsonObject("client");

            String jarUrl = client.get("url").getAsString();
            String sha1 = client.has("sha1") ? client.get("sha1").getAsString() : null;

            download(jarUrl, new File(versionDir, version + ".jar"), sha1);
        }

        // 3. Download Libraries
        downloadLibraries(root);

        // 4. Assets (if defined here)
        if (root.has("assetIndex")) {
            prepareAssets(root);
        }

        prepared.add(version);
        return root;
    }

    private static void downloadLibraries(JsonObject root) {
        if (!root.has("libraries")) {
            return;
        }
        JsonArray libs = root.getAsJsonArray("libraries");
        for (JsonElement e : libs) {
            try {
                JsonObject lib = e.getAsJsonObject();
                if (lib.has("downloads")) {
                    JsonObject downloads = lib.getAsJsonObject("downloads");
                    if (downloads.has("artifact")) {
                        JsonObject artifact = downloads.getAsJsonObject("artifact");
                        String path = artifact.get("path").getAsString();
                        String url = artifact.get("url").getAsString();

                        File target = new File(MC_DIR, "libraries/" + path);
                        String sha1 = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
                        if (!target.exists() || (sha1 != null && !checkHash(target, sha1))) {
                            System.out.println("Downloading lib: " + path);
                            download(url, target, sha1);
                        }
                    }
                    if (downloads.has("classifiers")) {
                        JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                        String nativeKey = null;
                        String osName = PlatformManager.getNativeClassifierOSName();

                        if (lib.has("natives") && lib.getAsJsonObject("natives").has(osName)) {
                            nativeKey = lib.getAsJsonObject("natives").get(osName).getAsString().replace("${arch}", "64");
                        }
                        if (nativeKey != null && classifiers.has(nativeKey)) {
                            JsonObject artifact = classifiers.getAsJsonObject(nativeKey);
                            String path = artifact.get("path").getAsString();
                            String url = artifact.get("url").getAsString();
                            File target = new File(MC_DIR, "libraries/" + path);
                            String sha1 = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
                            if (!target.exists() || (sha1 != null && !checkHash(target, sha1))) {
                                System.out.println("Downloading natives: " + path);
                                download(url, target, sha1);
                            }
                            extractNatives(target, getNativesDir());
                        }
                    }
                } else if (lib.has("name")) {
                    // Soporte para librerías Maven (común en Fabric)
                    String name = lib.get("name").getAsString();
                    String path = mavenToPath(name);
                    if (path != null) {
                        String baseUrl = lib.has("url") ? lib.get("url").getAsString()
                                : "https://libraries.minecraft.net/";
                        File target = new File(MC_DIR, "libraries/" + path);
                        if (!target.exists()) {
                            System.out.println("Downloading maven lib: " + name);
                            download(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + path, target);
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Failed to download library: " + ex.getMessage());
            }
        }
    }

    private static void extractNatives(File zipFile, File extractTo) {
        try {
            extractTo.mkdirs();
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile));
            java.util.zip.ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                if (!entry.isDirectory()
                        && (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib"))) {
                    File targetFile = new File(extractTo, new File(name).getName());
                    if (!targetFile.exists()) {
                        FileOutputStream fos = new FileOutputStream(targetFile);
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                    }
                }
                entry = zis.getNextEntry();
            }
            zis.close();
        } catch (Exception e) {
            System.err.println("Error extracting natives from " + zipFile.getName() + ": " + e.getMessage());
        }
    }

    private static String mavenToPath(String name) {
        String[] parts = name.split(":");
        if (parts.length >= 3) {
            String domain = parts[0].replace('.', '/');
            String artifact = parts[1];
            String val = parts[2];
            // Algunos nombres tienen clasificadores o extensiones (ej.
            // name:art:ver:class@ext)
            // Para simplificar, manejamos el caso estándar
            return domain + "/" + artifact + "/" + val + "/" + artifact + "-" + val + ".jar";
        }
        return null;
    }

    /* ================== CLASSPATH ================== */

    public static List<File> getClasspath(String version, JsonObject json) throws Exception {
        List<File> cp = new ArrayList<File>();

        // 1. Inheritance
        if (json.has("inheritsFrom")) {
            String parent = json.get("inheritsFrom").getAsString();
            File parentJsonFile = new File(MC_DIR, "versions/" + parent + "/" + parent + ".json");
            if (parentJsonFile.exists()) {
                JsonObject parentJson = JsonParser.parseReader(new FileReader(parentJsonFile)).getAsJsonObject();
                cp.addAll(getClasspath(parent, parentJson));
            }
        }

        // 2. Main JAR (if exists)
        File versionJar = new File(MC_DIR, "versions/" + version + "/" + version + ".jar");
        if (versionJar.exists()) {
            cp.add(versionJar);
        }

        // 3. Libraries
        if (json.has("libraries")) {
            JsonArray libs = json.getAsJsonArray("libraries");
            for (JsonElement e : libs) {
                try {
                    JsonObject lib = e.getAsJsonObject();
                    if (lib.has("downloads")) {
                        JsonObject downloads = lib.getAsJsonObject("downloads");
                        if (downloads.has("artifact")) {
                            String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                            cp.add(new File(MC_DIR, "libraries/" + path));
                        }
                    } else if (lib.has("name")) {
                        String path = mavenToPath(lib.get("name").getAsString());
                        if (path != null) {
                            File f = new File(MC_DIR, "libraries/" + path);
                            if (f.exists())
                                cp.add(f);
                        }
                    }
                } catch (Exception ex) {
                    // ignore malformed libs
                }
            }
        }
        return cp;
    }

    public static File getNativesDir() {
        return new File(MC_DIR, "libraries/natives");
    }

    /* ================== ASSETS ================== */

    private static void prepareAssets(JsonObject root) throws Exception {

        JsonObject assetIndex = root.getAsJsonObject("assetIndex");
        String id = assetIndex.get("id").getAsString();

        File indexFile = new File(MC_DIR, "assets/indexes/" + id + ".json");

        if (!indexFile.exists()) {
            System.out.println("Downloading asset index: " + id);
            download(assetIndex.get("url").getAsString(), indexFile);
        }

        JsonObject objects = JsonParser.parseReader(new FileReader(indexFile))
                .getAsJsonObject()
                .getAsJsonObject("objects");

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            String sub = hash.substring(0, 2);

            String url = "https://resources.download.minecraft.net/" + sub + "/" + hash;
            File target = new File(MC_DIR, "assets/objects/" + sub + "/" + hash);
            if (!target.exists() || !checkHash(target, hash)) {
                download(url, target, hash);
            }
        }
    }

    /* ================== HELPERS ================== */

    private static String getVersionUrl(String version) throws Exception {

        JsonArray versions = JsonParser.parseString(
                readUrl("https://piston-meta.mojang.com/mc/game/version_manifest.json")).getAsJsonObject()
                .getAsJsonArray("versions");

        for (JsonElement e : versions) {
            JsonObject v = e.getAsJsonObject();
            if (v.get("id").getAsString().equals(version)) {
                return v.get("url").getAsString();
            }
        }
        throw new RuntimeException("Version not found");
    }

    private static void download(String urlStr, File target) throws Exception {
        download(urlStr, target, null);
    }

    private static void download(String urlStr, File target, String expectedHash) throws Exception {
        int attempts = 3;
        Exception lastError = null;

        for (int i = 1; i <= attempts; i++) {
            try {
                downloadOnce(urlStr, target, expectedHash, i, attempts);
                return;
            } catch (Exception ex) {
                lastError = ex;
                System.err.println("[Download] Attempt " + i + "/" + attempts + " failed: " + ex.getMessage());

                try {
                    Thread.sleep(1000L * i);
                } catch (InterruptedException ignored) {
                }
            }
        }

        throw lastError == null ? new Exception("Unknown error downloading file.") : lastError;
    }

    private static void downloadOnce(String urlStr, File target, String expectedHash, int attempt, int attempts) throws Exception {
        if (target.exists()) {
            if (expectedHash == null || expectedHash.isEmpty() || checkHash(target, expectedHash)) {
                return;
            }

            System.out.println("[Download] Incorrect hash in existing file, deleting: " + target.getAbsolutePath());

            if (!target.delete()) {
                System.err.println("[Download] Could not delete corrupt file: " + target.getAbsolutePath());
            }
        }

        File parent = target.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(target.getAbsolutePath() + ".tmp");

        if (tempFile.exists()) {
            tempFile.delete();
        }

        System.out.println("[Download] Downloading (" + attempt + "/" + attempts + "): " + urlStr);
        System.out.println("[Download] Destination: " + target.getAbsolutePath());

        java.net.HttpURLConnection conn = null;
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;

        try {
            java.net.URL url = java.net.URI.create(urlStr).toURL();
            conn = (java.net.HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MinecraftLauncher/1.0");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                String body = "";

                try {
                    java.io.InputStream err = conn.getErrorStream();

                    if (err != null) {
                        java.util.Scanner scanner = new java.util.Scanner(err, "UTF-8").useDelimiter("\\A");
                        body = scanner.hasNext() ? scanner.next() : "";
                        scanner.close();
                        err.close();
                    }
                } catch (Exception ignored) {
                }

                throw new java.io.IOException("HTTP " + code + " downloading " + urlStr + ". " + body);
            }

            long contentLength = conn.getContentLengthLong();

            in = conn.getInputStream();
            out = new java.io.FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
            }

            out.close();
            out = null;

            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new java.io.IOException("Download was empty: " + urlStr);
            }

            if (contentLength > 0 && tempFile.length() != contentLength) {
                throw new java.io.IOException(
                        "Incomplete download. Expected: " + contentLength +
                                " bytes, received: " + tempFile.length() + " bytes."
                );
            }

            if (expectedHash != null && !expectedHash.isEmpty()) {
                if (!checkHash(tempFile, expectedHash)) {
                    String actual = getSha1(tempFile);

                    tempFile.delete();

                    throw new java.io.IOException(
                            "Failed to verify hash for " + target.getName() +
                                    ". Expected: " + expectedHash +
                                    ", actual: " + actual +
                                    ", url: " + urlStr
                    );
                }
            }

            if (target.exists()) {
                target.delete();
            }

            if (!tempFile.renameTo(target)) {
                throw new java.io.IOException("Could not move temporary file to destination: " + target.getAbsolutePath());
            }

            System.out.println("[Download] OK: " + target.getName());
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

            if (tempFile.exists() && (!target.exists() || target.length() == 0)) {
                tempFile.delete();
            }
        }
    }

    private static String getSha1(File file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");

        java.io.FileInputStream fis = new java.io.FileInputStream(file);

        try {
            byte[] dataBytes = new byte[8192];
            int nread;

            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }
        } finally {
            fis.close();
        }

        byte[] mdbytes = md.digest();

        StringBuilder sb = new StringBuilder();

        for (byte b : mdbytes) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();
    }

    private static boolean checkHash(File file, String expected) {
        try {
            if (file == null || !file.exists() || expected == null || expected.trim().isEmpty()) {
                return false;
            }

            String actual = getSha1(file);

            return actual.equalsIgnoreCase(expected.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonObject repairVersion(String version) throws Exception {
        System.out.println("[Repair] Repairing version: " + version);

        prepared.remove(version);

        File nativesDir = getNativesDir();

        if (nativesDir.exists()) {
            System.out.println("[Repair] Cleaning natives...");
            deleteDirectory(nativesDir);
        }

        JsonObject repaired = repairVersionRecursive(version, new HashSet<String>());

        prepared.add(version);

        System.out.println("[Repair] Repair finished: " + version);

        return repaired;
    }

    private static JsonObject repairVersionRecursive(String version, Set<String> visited) throws Exception {
        if (visited.contains(version)) {
            File localJson = new File(MC_DIR, "versions/" + version + "/" + version + ".json");
            return JsonParser.parseReader(new FileReader(localJson)).getAsJsonObject();
        }

        visited.add(version);

        File versionDir = new File(MC_DIR, "versions/" + version);
        versionDir.mkdirs();

        File jsonFile = new File(versionDir, version + ".json");

        JsonObject root;

        if (jsonFile.exists()) {
            try {
                root = JsonParser.parseReader(new FileReader(jsonFile)).getAsJsonObject();
            } catch (Exception ex) {
                System.err.println("[Repair] Corrupt JSON, attempting to download again: " + version);
                jsonFile.delete();

                String versionUrl = getVersionUrl(version);
                String jsonText = readUrl(versionUrl);
                writeFile(jsonFile, jsonText);
                root = JsonParser.parseString(jsonText).getAsJsonObject();
            }
        } else {
            String versionUrl = getVersionUrl(version);
            String jsonText = readUrl(versionUrl);
            writeFile(jsonFile, jsonText);
            root = JsonParser.parseString(jsonText).getAsJsonObject();
        }

        if (root.has("inheritsFrom")) {
            String parent = root.get("inheritsFrom").getAsString();
            System.out.println("[Repair] " + version + " inherits from " + parent);
            repairVersionRecursive(parent, visited);
        }

        if (root.has("downloads") && root.getAsJsonObject("downloads").has("client")) {
            JsonObject client = root.getAsJsonObject("downloads").getAsJsonObject("client");

            String jarUrl = client.get("url").getAsString();
            String sha1 = client.has("sha1") ? client.get("sha1").getAsString() : null;

            File jarFile = new File(versionDir, version + ".jar");

            System.out.println("[Repair] Verifying client jar: " + jarFile.getName());
            download(jarUrl, jarFile, sha1);
        }

        System.out.println("[Repair] Verifying libraries...");
        downloadLibraries(root);

        System.out.println("[Repair] Re-extracting natives...");
        extractNativesFromJson(root);

        if (root.has("assetIndex")) {
            System.out.println("[Repair] Verifying assets...");
            prepareAssets(root);
        }

        prepared.add(version);

        return root;
    }

    private static void extractNativesFromJson(JsonObject root) {
        if (root == null || !root.has("libraries")) {
            return;
        }

        JsonArray libs = root.getAsJsonArray("libraries");

        for (JsonElement e : libs) {
            try {
                JsonObject lib = e.getAsJsonObject();

                if (!lib.has("downloads")) {
                    continue;
                }

                JsonObject downloads = lib.getAsJsonObject("downloads");

                if (!downloads.has("classifiers")) {
                    continue;
                }

                JsonObject classifiers = downloads.getAsJsonObject("classifiers");

                String nativeKey = null;

                if (lib.has("natives") && lib.getAsJsonObject("natives").has("windows")) {
                    nativeKey = lib.getAsJsonObject("natives").get("windows").getAsString().replace("${arch}", "64");
                }

                if (nativeKey == null || !classifiers.has(nativeKey)) {
                    continue;
                }

                JsonObject artifact = classifiers.getAsJsonObject(nativeKey);
                String path = artifact.get("path").getAsString();

                File target = new File(MC_DIR, "libraries/" + path);

                if (target.exists()) {
                    extractNatives(target, getNativesDir());
                }
            } catch (Exception ex) {
                System.err.println("[Repair] Could not extract native: " + ex.getMessage());
            }
        }
    }

    private static void deleteDirectory(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }

        if (!file.delete()) {
            System.err.println("[Repair] Could not delete: " + file.getAbsolutePath());
        }
    }

    private static String readUrl(String urlStr) throws Exception {

        System.out.println("Fetching: " + urlStr);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        
        int responseCode = conn.getResponseCode();
        System.out.println("Response code: " + responseCode);
        
        if (responseCode != 200) {
            throw new Exception("HTTP " + responseCode);
        }
        
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        return sb.toString();
    }

    private static void writeFile(File file, String text) throws Exception {

        file.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(file);
        out.write(text.getBytes("UTF-8"));
        out.close();
    }
}
