package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CurseForgeClient {

    private static final String API_BASE = "https://api.curseforge.com/v1";
    private static final String USER_AGENT = "ProfessionalMinecraftLauncher/1.0";
    private static final int MINECRAFT_GAME_ID = 432;

    /*
     * CurseForge class IDs for Minecraft:
     * Mods: 6
     * Resource Packs: 12
     * Shaders: 6552
     */
    private static int getClassId(String projectType) {
        if ("resourcepack".equalsIgnoreCase(projectType)) {
            return 12;
        }

        if ("shader".equalsIgnoreCase(projectType)) {
            return 6552;
        }

        return 6;
    }

    public static boolean validateApiKey(String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("API Key vacía.");
        }

        String cleanKey = apiKey.trim();
        String url = API_BASE + "/games/" + MINECRAFT_GAME_ID;

        JsonObject response = readJsonObject(cleanKey, url);

        return response.has("data");
    }

    public static List<ModrinthClient.ModResult> searchProjects(String apiKey,
                                                                String query,
                                                                String projectType) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("CurseForge API Key no configurada.");
        }

        if (projectType == null || projectType.trim().isEmpty()) {
            projectType = "mod";
        }

        StringBuilder url = new StringBuilder();

        url.append(API_BASE)
                .append("/mods/search")
                .append("?gameId=").append(MINECRAFT_GAME_ID)
                .append("&classId=").append(getClassId(projectType))
                .append("&pageSize=20")
                .append("&sortField=6")
                .append("&sortOrder=desc");

        if (query != null && !query.trim().isEmpty()) {
            url.append("&searchFilter=").append(encode(query.trim()));
        }

        JsonObject response = readJsonObject(apiKey, url.toString());

        if (!response.has("data") || !response.get("data").isJsonArray()) {
            throw new Exception("Respuesta inválida de CurseForge.");
        }

        JsonArray data = response.getAsJsonArray("data");
        List<ModrinthClient.ModResult> results = new ArrayList<ModrinthClient.ModResult>();

        for (JsonElement element : data) {
            try {
                JsonObject obj = element.getAsJsonObject();

                String id = getString(obj, "id", "");
                String slug = getString(obj, "slug", "");
                String name = getString(obj, "name", "Unknown");
                String summary = getString(obj, "summary", "");
                String iconUrl = "";

                if (obj.has("logo") && obj.get("logo").isJsonObject()) {
                    JsonObject logo = obj.getAsJsonObject("logo");
                    iconUrl = getString(logo, "url", "");
                }

                if (id == null || id.trim().isEmpty()) {
                    continue;
                }

                ModrinthClient.ModResult result = new ModrinthClient.ModResult(
                        slug,
                        name,
                        summary,
                        "curseforge:" + id,
                        projectType
                );

                result.iconUrl = iconUrl;
                results.add(result);
            } catch (Exception ex) {
                System.err.println("[CurseForge] Error parseando resultado: " + ex.getMessage());
            }
        }

        System.out.println("[CurseForge] Resultados encontrados: " + results.size() + " | tipo=" + projectType);

        return results;
    }

    public static List<ModrinthClient.ModResult> searchPopularProjects(String apiKey,
                                                                       String projectType) throws Exception {
        return searchProjects(apiKey, "", projectType);
    }

    public static ModrinthClient.ModVersionFile getLatestVersionFile(String apiKey,
                                                                     String projectId,
                                                                     String gameVersion,
                                                                     String projectType) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("CurseForge API Key no configurada.");
        }

        String numericId = stripCurseForgePrefix(projectId);

        if (numericId == null || numericId.trim().isEmpty()) {
            throw new Exception("Project ID de CurseForge inválido.");
        }

        if (projectType == null || projectType.trim().isEmpty()) {
            projectType = "mod";
        }

        JsonArray files = requestFiles(apiKey, numericId, gameVersion, projectType, true);

        if (files == null || files.size() == 0) {
            files = requestFiles(apiKey, numericId, gameVersion, projectType, false);
        }

        if (files == null || files.size() == 0) {
            files = requestFiles(apiKey, numericId, null, projectType, true);
        }

        if (files == null || files.size() == 0) {
            files = requestFiles(apiKey, numericId, null, projectType, false);
        }

        if (files == null || files.size() == 0) {
            throw new Exception("No se encontraron archivos descargables en CurseForge.");
        }

        JsonObject selectedFile = selectBestFile(files, projectType);

        String fileId = getString(selectedFile, "id", "");
        String fileName = getString(selectedFile, "fileName", "");
        String downloadUrl = getString(selectedFile, "downloadUrl", "");

        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            downloadUrl = requestDownloadUrl(apiKey, numericId, fileId);
        }

        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            throw new Exception("CurseForge no devolvió URL de descarga para " + fileName);
        }

        List<String> dependencies = extractRequiredDependencies(selectedFile);

        return new ModrinthClient.ModVersionFile(
                downloadUrl,
                fileName,
                fileId,
                "curseforge:" + numericId,
                projectType,
                dependencies
        );
    }

    private static JsonArray requestFiles(String apiKey,
                                          String numericProjectId,
                                          String gameVersion,
                                          String projectType,
                                          boolean fabricOnly) throws Exception {
        StringBuilder url = new StringBuilder();

        url.append(API_BASE)
                .append("/mods/")
                .append(encode(numericProjectId))
                .append("/files")
                .append("?pageSize=20");

        if (gameVersion != null && !gameVersion.trim().isEmpty()) {
            url.append("&gameVersion=").append(encode(gameVersion.trim()));
        }

        /*
         * CurseForge modLoaderType:
         * 4 = Fabric.
         * Only apply to mods, not resource packs or shaders.
         */
        if (fabricOnly && "mod".equalsIgnoreCase(projectType)) {
            url.append("&modLoaderType=4");
        }

        JsonObject response = readJsonObject(apiKey, url.toString());

        if (!response.has("data") || !response.get("data").isJsonArray()) {
            return new JsonArray();
        }

        return response.getAsJsonArray("data");
    }

    private static JsonObject selectBestFile(JsonArray files, String projectType) throws Exception {
        JsonObject fallback = null;

        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();

            String fileName = getString(file, "fileName", "").toLowerCase();
            String downloadUrl = getString(file, "downloadUrl", "");

            boolean extOk =
                    ("mod".equalsIgnoreCase(projectType) && fileName.endsWith(".jar")) ||
                            ("resourcepack".equalsIgnoreCase(projectType) && fileName.endsWith(".zip")) ||
                            ("shader".equalsIgnoreCase(projectType) && fileName.endsWith(".zip"));

            if (!extOk) {
                continue;
            }

            if (fallback == null) {
                fallback = file;
            }

            if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                return file;
            }
        }

        if (fallback != null) {
            return fallback;
        }

        if (files.size() > 0) {
            return files.get(0).getAsJsonObject();
        }

        throw new Exception("No hay archivos válidos.");
    }

    private static List<String> extractRequiredDependencies(JsonObject file) {
        List<String> deps = new ArrayList<String>();

        try {
            if (!file.has("dependencies") || !file.get("dependencies").isJsonArray()) {
                return deps;
            }

            JsonArray arr = file.getAsJsonArray("dependencies");

            for (JsonElement element : arr) {
                JsonObject dep = element.getAsJsonObject();

                int relationType = dep.has("relationType") ? dep.get("relationType").getAsInt() : -1;

                /*
                 * CurseForge relationType:
                 * 3 = requiredDependency.
                 */
                if (relationType != 3) {
                    continue;
                }

                String modId = getString(dep, "modId", "");

                if (modId != null && !modId.trim().isEmpty()) {
                    String projectId = "curseforge:" + modId.trim();

                    if (!deps.contains(projectId)) {
                        deps.add(projectId);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return deps;
    }

    private static String requestDownloadUrl(String apiKey,
                                             String numericProjectId,
                                             String fileId) throws Exception {
        if (fileId == null || fileId.trim().isEmpty()) {
            return "";
        }

        String url = API_BASE
                + "/mods/"
                + encode(numericProjectId)
                + "/files/"
                + encode(fileId)
                + "/download-url";

        try {
            JsonObject response = readJsonObject(apiKey, url);

            if (!response.has("data") || response.get("data").isJsonNull()) {
                return "";
            }

            return response.get("data").getAsString();
        } catch (Exception ex) {
            throw new Exception(
                    "No se pudo obtener la URL de descarga desde CurseForge. " +
                            "Puede que el archivo no permita distribución externa o que la API Key no tenga acceso. " +
                            ex.getMessage()
            );
        }
    }

    private static JsonObject readJsonObject(String apiKey,
                                             String urlString) throws Exception {
        String text = readUrl(apiKey, urlString);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static String readUrl(String apiKey,
                                  String urlString) throws Exception {
        System.out.println("[CurseForge] GET " + urlString);

        HttpURLConnection conn = null;
        InputStream stream = null;

        try {
            conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            if (code >= 200 && code < 300) {
                stream = conn.getInputStream();
            } else {
                stream = conn.getErrorStream();

                String errorBody = stream == null ? "" : readStream(stream);

                if (code == 401 || code == 403) {
                    throw new Exception(
                            "CurseForge rechazó la petición con HTTP " + code + ". " +
                                    "La API Key puede ser inválida, estar mal copiada o no tener permisos. " +
                                    "Respuesta: " + errorBody
                    );
                }

                throw new Exception("CurseForge HTTP " + code + " - " + conn.getResponseMessage() + ". " + errorBody);
            }

            return readStream(stream);
        } finally {
            if (stream != null) {
                stream.close();
            }

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

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

    private static String stripCurseForgePrefix(String projectId) {
        if (projectId == null) {
            return "";
        }

        String id = projectId.trim();

        if (id.toLowerCase().startsWith("curseforge:")) {
            return id.substring("curseforge:".length());
        }

        return id;
    }

    private static String getString(JsonObject obj,
                                    String key,
                                    String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    public static List<ModrinthClient.ModVersionFile> getVersionFiles(String apiKey,
                                                                      String projectId,
                                                                      String gameVersion,
                                                                      String projectType) throws Exception {
        String numericId = stripCurseForgePrefix(projectId);

        if (projectType == null || projectType.trim().isEmpty()) {
            projectType = "mod";
        }

        JsonArray files = requestFiles(apiKey, numericId, gameVersion, projectType, true);

        if (files == null || files.size() == 0) {
            files = requestFiles(apiKey, numericId, gameVersion, projectType, false);
        }

        if (files == null || files.size() == 0) {
            files = requestFiles(apiKey, numericId, null, projectType, true);
        }

        if (files == null || files.size() == 0) {
            files = requestFiles(apiKey, numericId, null, projectType, false);
        }

        List<ModrinthClient.ModVersionFile> result = new ArrayList<ModrinthClient.ModVersionFile>();

        for (JsonElement element : files) {
            try {
                JsonObject fileObj = element.getAsJsonObject();

                String fileId = getString(fileObj, "id", "");
                String fileName = getString(fileObj, "fileName", "");
                String displayName = getString(fileObj, "displayName", fileName);
                String downloadUrl = getString(fileObj, "downloadUrl", "");

                if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                    downloadUrl = requestDownloadUrl(apiKey, numericId, fileId);
                }

                if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                    continue;
                }

                List<String> dependencies = extractRequiredDependencies(fileObj);

                ModrinthClient.ModVersionFile file = new ModrinthClient.ModVersionFile(
                        downloadUrl,
                        fileName,
                        fileId,
                        "curseforge:" + numericId,
                        projectType,
                        dependencies
                );

                file.versionId = displayName;

                result.add(file);
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}