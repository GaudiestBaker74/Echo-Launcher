package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModrinthClient {

    private static final String API_BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "ProfessionalMinecraftLauncher/1.0";

    public static class ModResult {
        public String slug;
        public String title;
        public String description;
        public String projectId;
        public String iconUrl;
        public String projectType;
        public List<String> categories;
        public List<String> versions;

        public ModResult(String slug, String title, String description, String projectId) {
            this(slug, title, description, projectId, "mod");
        }

        public ModResult(String slug, String title, String description, String projectId, String projectType) {
            this.slug = slug;
            this.title = title;
            this.description = description;
            this.projectId = projectId;
            this.projectType = projectType;
            this.categories = new ArrayList<String>();
            this.versions = new ArrayList<String>();
        }

        @Override
        public String toString() {
            return title + " (" + slug + ")";
        }
    }

    public static class ModVersionFile {
        public String url;
        public String filename;
        public String versionId;
        public String projectId;
        public String projectType;
        public List<String> dependencyProjectIds;

        public ModVersionFile(String url, String filename) {
            this.url = url;
            this.filename = filename;
            this.dependencyProjectIds = new ArrayList<String>();
        }

        public ModVersionFile(String url, String filename, String versionId, String projectId, String projectType, List<String> dependencyProjectIds) {
            this.url = url;
            this.filename = filename;
            this.versionId = versionId;
            this.projectId = projectId;
            this.projectType = projectType;
            this.dependencyProjectIds = dependencyProjectIds == null ? new ArrayList<String>() : dependencyProjectIds;
        }
    }

    public static List<ModResult> searchMods(String query) throws Exception {
        return searchProjects(query, "mod");
    }

    public static List<ModResult> searchProjects(String query, String projectType) throws Exception {
        if (query == null || query.trim().isEmpty()) {
            return searchPopularProjects(projectType);
        }

        if (projectType == null || projectType.trim().isEmpty()) {
            projectType = "mod";
        }

        String facets = "[[\"project_type:" + projectType + "\"]]";
        String urlString = API_BASE
                + "/search?query=" + encode(query.trim())
                + "&limit=20"
                + "&facets=" + encode(facets);

        return parseSearchResponse(readJsonObject(urlString), projectType);
    }

    public static List<ModResult> searchPopularProjects(String projectType) throws Exception {
        if (projectType == null || projectType.trim().isEmpty()) {
            projectType = "mod";
        }

        String facets = "[[\"project_type:" + projectType + "\"]]";
        String urlString = API_BASE
                + "/search?limit=20"
                + "&index=downloads"
                + "&facets=" + encode(facets);

        return parseSearchResponse(readJsonObject(urlString), projectType);
    }

    private static List<ModResult> parseSearchResponse(JsonObject response, String expectedProjectType) throws Exception {
        if (!response.has("hits") || !response.get("hits").isJsonArray()) {
            throw new Exception("Respuesta inválida de Modrinth: no existe el array 'hits'.");
        }

        JsonArray hits = response.getAsJsonArray("hits");
        List<ModResult> results = new ArrayList<ModResult>();

        for (JsonElement element : hits) {
            try {
                JsonObject hit = element.getAsJsonObject();

                String hitType = getString(hit, "project_type", expectedProjectType);

                if (expectedProjectType != null && !expectedProjectType.equalsIgnoreCase(hitType)) {
                    continue;
                }

                String slug = getString(hit, "slug", "");
                String title = getString(hit, "title", "Unknown");
                String description = getString(hit, "description", "");
                String projectId = getString(hit, "project_id", "");

                if (projectId.isEmpty()) {
                    continue;
                }

                ModResult result = new ModResult(slug, title, description, projectId, hitType);
                result.iconUrl = getString(hit, "icon_url", "");

                if (hit.has("categories") && hit.get("categories").isJsonArray()) {
                    JsonArray categories = hit.getAsJsonArray("categories");

                    for (JsonElement c : categories) {
                        result.categories.add(c.getAsString());
                    }
                }

                if (hit.has("versions") && hit.get("versions").isJsonArray()) {
                    JsonArray versions = hit.getAsJsonArray("versions");

                    for (JsonElement v : versions) {
                        result.versions.add(v.getAsString());
                    }
                }

                results.add(result);
            } catch (Exception ex) {
                System.err.println("[Modrinth] Error parseando resultado: " + ex.getMessage());
            }
        }

        System.out.println("[Modrinth] Resultados encontrados: " + results.size() + " | tipo=" + expectedProjectType);

        return results;
    }

    public static ModVersionFile getLatestVersionFile(String projectId, String gameVersion) throws Exception {
        return getLatestVersionFile(projectId, gameVersion, "mod");
    }

    public static ModVersionFile getLatestVersionFile(String projectId, String gameVersion, String projectType) throws Exception {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new Exception("Project ID inválido.");
        }

        if (projectType == null || projectType.trim().isEmpty()) {
            projectType = "mod";
        }

        String normalizedGameVersion = normalizeMinecraftVersion(gameVersion);
        JsonArray versions = null;

        if ("mod".equalsIgnoreCase(projectType)) {
            if (normalizedGameVersion != null && !normalizedGameVersion.isEmpty()) {
                versions = requestVersions(projectId, normalizedGameVersion, "fabric");
            }

            if (versions == null || versions.size() == 0) {
                versions = requestVersions(projectId, null, "fabric");
            }

            if (versions == null || versions.size() == 0) {
                versions = requestVersions(projectId, normalizedGameVersion, null);
            }

            if (versions == null || versions.size() == 0) {
                versions = requestVersions(projectId, null, null);
            }
        } else {
            if (normalizedGameVersion != null && !normalizedGameVersion.isEmpty()) {
                versions = requestVersions(projectId, normalizedGameVersion, null);
            }

            if (versions == null || versions.size() == 0) {
                versions = requestVersions(projectId, null, null);
            }
        }

        if (versions == null || versions.size() == 0) {
            throw new Exception("No se encontraron versiones descargables para este contenido.");
        }

        JsonObject selectedVersion = versions.get(0).getAsJsonObject();

        String selectedVersionId = getString(selectedVersion, "id", "");

        List<String> dependencies = new ArrayList<String>();

        if (selectedVersion.has("dependencies") && selectedVersion.get("dependencies").isJsonArray()) {
            JsonArray deps = selectedVersion.getAsJsonArray("dependencies");

            for (JsonElement depElement : deps) {
                try {
                    JsonObject dep = depElement.getAsJsonObject();

                    String dependencyType = getString(dep, "dependency_type", "");

                    if (!"required".equalsIgnoreCase(dependencyType)) {
                        continue;
                    }

                    String depProjectId = getString(dep, "project_id", "");

                    if (depProjectId != null && !depProjectId.trim().isEmpty()) {
                        if (!dependencies.contains(depProjectId)) {
                            dependencies.add(depProjectId);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!selectedVersion.has("files") || !selectedVersion.get("files").isJsonArray()) {
            throw new Exception("La versión seleccionada no contiene archivos.");
        }

        JsonArray files = selectedVersion.getAsJsonArray("files");

        if (files.size() == 0) {
            throw new Exception("No se encontró ningún archivo descargable.");
        }

        JsonObject selectedFile = null;

        for (JsonElement f : files) {
            JsonObject fileObj = f.getAsJsonObject();

            if (fileObj.has("primary") && fileObj.get("primary").getAsBoolean()) {
                selectedFile = fileObj;
                break;
            }
        }

        if (selectedFile == null) {
            for (JsonElement f : files) {
                JsonObject fileObj = f.getAsJsonObject();
                String filename = getString(fileObj, "filename", "").toLowerCase();

                if ("mod".equalsIgnoreCase(projectType) && filename.endsWith(".jar")) {
                    selectedFile = fileObj;
                    break;
                }

                if ("resourcepack".equalsIgnoreCase(projectType) && filename.endsWith(".zip")) {
                    selectedFile = fileObj;
                    break;
                }

                if ("shader".equalsIgnoreCase(projectType) && filename.endsWith(".zip")) {
                    selectedFile = fileObj;
                    break;
                }
                if ("modpack".equalsIgnoreCase(projectType) && filename.endsWith(".mrpack")) {
                    selectedFile = fileObj;
                    break;
                }
                if ("modpack".equalsIgnoreCase(projectType)) {
                    filename = "modrinth-modpack.mrpack";
                }
            }
        }

        if (selectedFile == null) {
            selectedFile = files.get(0).getAsJsonObject();
        }

        String url = getString(selectedFile, "url", "");
        String filename = getString(selectedFile, "filename", "");

        if (url.isEmpty()) {
            throw new Exception("El archivo seleccionado no tiene URL.");
        }

        if (filename.isEmpty()) {
            if ("mod".equalsIgnoreCase(projectType)) {
                filename = "modrinth-mod.jar";
            } else {
                filename = "modrinth-content.zip";
            }
        }

        return new ModVersionFile(
                url,
                filename,
                selectedVersionId,
                projectId,
                projectType,
                dependencies
        );
    }

    private static JsonArray requestVersions(String projectId, String gameVersion, String loader) throws Exception {
        StringBuilder url = new StringBuilder();

        url.append(API_BASE)
                .append("/project/")
                .append(encode(projectId))
                .append("/version");

        List<String> params = new ArrayList<String>();

        if (gameVersion != null && !gameVersion.trim().isEmpty()) {
            params.add("game_versions=" + encode("[\"" + gameVersion.trim() + "\"]"));
        }

        if (loader != null && !loader.trim().isEmpty()) {
            params.add("loaders=" + encode("[\"" + loader.trim() + "\"]"));
        }

        if (!params.isEmpty()) {
            url.append("?");

            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    url.append("&");
                }

                url.append(params.get(i));
            }
        }

        return readJsonArray(url.toString());
    }

    public static List<ModVersionFile> getVersionFiles(String projectId,
                                                       String gameVersion,
                                                       String projectType) throws Exception {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new Exception("Project ID inválido.");
        }

        if (projectType == null || projectType.trim().isEmpty()) {
            projectType = "mod";
        }

        String normalizedGameVersion = normalizeMinecraftVersion(gameVersion);
        JsonArray versions = null;

        if ("mod".equalsIgnoreCase(projectType)) {
            if (normalizedGameVersion != null && !normalizedGameVersion.isEmpty()) {
                versions = requestVersions(projectId, normalizedGameVersion, "fabric");
            }

            if (versions == null || versions.size() == 0) {
                versions = requestVersions(projectId, normalizedGameVersion, null);
            }

            if (versions == null || versions.size() == 0) {
                versions = requestVersions(projectId, null, "fabric");
            }
        } else {
            if (normalizedGameVersion != null && !normalizedGameVersion.isEmpty()) {
                versions = requestVersions(projectId, normalizedGameVersion, null);
            }
        }

        if (versions == null || versions.size() == 0) {
            versions = requestVersions(projectId, null, null);
        }

        List<ModVersionFile> result = new ArrayList<ModVersionFile>();

        for (JsonElement element : versions) {
            try {
                JsonObject versionObj = element.getAsJsonObject();

                String versionId = getString(versionObj, "id", "");
                String versionName = getString(versionObj, "name", versionId);
                String versionNumber = getString(versionObj, "version_number", "");

                List<String> dependencies = new ArrayList<String>();

                if (versionObj.has("dependencies") && versionObj.get("dependencies").isJsonArray()) {
                    JsonArray deps = versionObj.getAsJsonArray("dependencies");

                    for (JsonElement depElement : deps) {
                        JsonObject dep = depElement.getAsJsonObject();

                        String dependencyType = getString(dep, "dependency_type", "");

                        if (!"required".equalsIgnoreCase(dependencyType)) {
                            continue;
                        }

                        String depProjectId = getString(dep, "project_id", "");

                        if (depProjectId != null && !depProjectId.trim().isEmpty() && !dependencies.contains(depProjectId)) {
                            dependencies.add(depProjectId);
                        }
                    }
                }

                if (!versionObj.has("files") || !versionObj.get("files").isJsonArray()) {
                    continue;
                }

                JsonArray files = versionObj.getAsJsonArray("files");

                JsonObject selectedFile = null;

                for (JsonElement f : files) {
                    JsonObject fileObj = f.getAsJsonObject();

                    if (fileObj.has("primary") && fileObj.get("primary").getAsBoolean()) {
                        selectedFile = fileObj;
                        break;
                    }
                }

                if (selectedFile == null && files.size() > 0) {
                    selectedFile = files.get(0).getAsJsonObject();
                }

                if (selectedFile == null) {
                    continue;
                }

                String url = getString(selectedFile, "url", "");
                String filename = getString(selectedFile, "filename", "");

                if (url.isEmpty() || filename.isEmpty()) {
                    continue;
                }

                ModVersionFile file = new ModVersionFile(
                        url,
                        filename,
                        versionId,
                        projectId,
                        projectType,
                        dependencies
                );

                file.versionId = versionName + (versionNumber.isEmpty() ? "" : " (" + versionNumber + ")");

                result.add(file);
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private static String normalizeMinecraftVersion(String version) {
        if (version == null) {
            return null;
        }

        version = version.trim();

        if (version.isEmpty()) {
            return null;
        }

        if (version.matches("\\d+\\.\\d+(\\.\\d+)?")) {
            return version;
        }

        String[] parts = version.split("-");

        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+\\.\\d+(\\.\\d+)?")) {
                return parts[i];
            }
        }

        return version;
    }

    private static JsonObject readJsonObject(String urlString) throws Exception {
        String text = readUrl(urlString);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonArray readJsonArray(String urlString) throws Exception {
        String text = readUrl(urlString);
        return JsonParser.parseString(text).getAsJsonArray();
    }

    private static String readUrl(String urlString) throws Exception {
        System.out.println("[Modrinth] GET " + urlString);

        HttpURLConnection conn = null;

        try {
            URL url = java.net.URI.create(urlString).toURL();
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

                throw new Exception("HTTP " + code + " - " + conn.getResponseMessage() + ". " + errorBody);
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

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}