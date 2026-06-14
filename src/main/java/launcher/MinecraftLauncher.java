package launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MinecraftLauncher {

    /*
     * Método antiguo.
     * Lanza usando .minecraft como gameDir.
     */
    public static void launch(String version, String user, int ram) throws Exception {
        launchInternal(version, user, ram, VersionManager.MC_DIR);
    }

    /*
     * Método nuevo para instancias tipo Prism.
     * Lanza usando la carpeta de la instancia como gameDir.
     */
    public static void launch(String version, String user, int ram, File gameDir) throws Exception {
        launchInternal(version, user, ram, gameDir);
    }

    private static void launchInternal(String version, String user, int ram, File gameDir) throws Exception {
        if (gameDir == null) {
            gameDir = VersionManager.MC_DIR;
        }

        gameDir.mkdirs();

        JsonObject json = VersionManager.prepareVersion(version);
        List<File> cp = VersionManager.getClasspath(version, json);

        StringBuilder classpath = new StringBuilder();

        for (File f : cp) {
            classpath.append(f.getAbsolutePath()).append(File.pathSeparator);
        }

        List<String> cmd = new ArrayList<String>();

        String javaPath = getJavaPath(version, json);
        cmd.add(javaPath);

        /*
         * RAM
         */
        cmd.add("-Xms" + ram + "G");
        cmd.add("-Xmx" + ram + "G");

        /*
         * JVM args generales.
         */
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:MaxGCPauseMillis=50");
        cmd.add("-XX:+UnlockExperimentalVMOptions");
        cmd.add("-XX:G1NewSizePercent=20");
        cmd.add("-XX:G1ReservePercent=20");
        cmd.add("-XX:InitiatingHeapOccupancyPercent=15");
        cmd.add("-XX:+AlwaysPreTouch");
        cmd.add("-XX:+ParallelRefProcEnabled");
        cmd.add("-Dsun.rmi.dgc.server.gcInterval=2147483646");
        cmd.add("-Dsun.rmi.dgc.client.gcInterval=2147483646");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dsun.stdout.encoding=UTF-8");
        cmd.add("-Dsun.stderr.encoding=UTF-8");
        cmd.add("-Dorg.fusesource.jansi.Ansi.disable=true");
        cmd.add("-Dlog4j.skipJansi=true");

        /*
         * Natives globales.
         */
        cmd.add("-Djava.library.path=" + VersionManager.getNativesDir().getAbsolutePath());

        /*
         * Classpath.
         */
        cmd.add("-cp");
        cmd.add(classpath.toString());

        /*
         * Main class.
         */
        if (!json.has("mainClass")) {
            throw new Exception("El JSON de la versión no contiene mainClass.");
        }

        cmd.add(json.get("mainClass").getAsString());

        /*
         * Argumentos Minecraft.
         */
        cmd.add("--username");
        cmd.add(user);

        cmd.add("--version");
        cmd.add(version);

        cmd.add("--gameDir");
        cmd.add(gameDir.getAbsolutePath());

        cmd.add("--assetsDir");
        cmd.add(new File(VersionManager.MC_DIR, "assets").getAbsolutePath());

        cmd.add("--assetIndex");
        cmd.add(resolveAssetIndex(version, json));

        cmd.add("--accessToken");
        cmd.add("0");

        /*
         * Args requeridos por versiones antiguas.
         */
        cmd.add("--userProperties");
        cmd.add("{}");

        cmd.add("--userType");
        cmd.add("mojang");

        System.out.println("[LAUNCHER-DEBUG] Ejecutando Minecraft con Java:");
        System.out.println(javaPath);

        System.out.println("[LAUNCHER-DEBUG] GameDir:");
        System.out.println(gameDir.getAbsolutePath());

        System.out.println("[LAUNCHER-DEBUG] Argumentos:");
        System.out.println(String.join(" ", cmd));

        final Process process = new ProcessBuilder(cmd)
                .directory(gameDir)
                .start();

        final StringBuilder gameLog = new StringBuilder();

        final Thread stdoutThread = pipeProcessStream(
                process.getInputStream(),
                "[GAME] ",
                gameLog,
                false
        );

        final Thread stderrThread = pipeProcessStream(
                process.getErrorStream(),
                "[GAME-ERROR] ",
                gameLog,
                true
        );

        System.out.println("[LAUNCHER-DEBUG] Proceso de Minecraft iniciado. PID disponible: " + process.pid());

        /*
         * Esperamos unos segundos para detectar crashes inmediatos.
         * Si no termina en ese tiempo, asumimos que el juego abrió correctamente.
         */
        boolean exitedQuickly = process.waitFor(8, TimeUnit.SECONDS);

        if (exitedQuickly) {
            int exitCode = process.exitValue();

            try {
                stdoutThread.join(3000);
                stderrThread.join(3000);
            } catch (InterruptedException ignored) {
            }

            System.out.println("[LAUNCHER-DEBUG] Minecraft terminó rápidamente con código: " + exitCode);

            if (exitCode != 0) {
                CrashAnalyzer.CrashInfo crashInfo = CrashAnalyzer.analyze(gameLog.toString(), exitCode);
                throw new CrashAnalyzer.CrashException(crashInfo, gameLog.toString(), exitCode);
            }

            throw new Exception("Minecraft se cerró inmediatamente sin mostrar ventana. Revisa el log para más detalles.");
        }

        /*
         * Si sigue vivo después de 8 segundos, lo dejamos corriendo.
         * Creamos un watcher en segundo plano solo para registrar cuándo cierre.
         */
        Thread watcher = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int exitCode = process.waitFor();

                    try {
                        stdoutThread.join(3000);
                        stderrThread.join(3000);
                    } catch (InterruptedException ignored) {
                    }

                    System.out.println("[LAUNCHER-DEBUG] Minecraft terminó con código: " + exitCode);

                    if (exitCode != 0) {
                        CrashAnalyzer.CrashInfo crashInfo = CrashAnalyzer.analyze(gameLog.toString(), exitCode);

                        System.err.println("[CRASH-ANALYZER] " + crashInfo.title);
                        System.err.println("[CRASH-ANALYZER] Causa: " + crashInfo.cause);
                        System.err.println("[CRASH-ANALYZER] Solución: " + crashInfo.solution);
                        System.err.println("[CRASH-ANALYZER] Detalles:");
                        System.err.println(crashInfo.details);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, "Minecraft-Process-Watcher");

        watcher.setDaemon(true);
        watcher.start();

        System.out.println("[LAUNCHER-DEBUG] Minecraft sigue ejecutándose. El launcher queda libre.");
    }

    private static String getJavaPath(String version, JsonObject json) throws Exception {
        int requiredJava = resolveRequiredJavaMajor(version, json);

        System.out.println("[JavaRuntime] Minecraft " + version + " requiere Java " + requiredJava);

        return JavaRuntimeManager.getJava(requiredJava);
    }

    private static int resolveRequiredJavaMajor(String version, JsonObject json) {
        try {
            if (json != null && json.has("javaVersion")) {
                JsonObject javaVersion = json.getAsJsonObject("javaVersion");

                if (javaVersion.has("majorVersion")) {
                    int major = javaVersion.get("majorVersion").getAsInt();

                    if (major > 0) {
                        return major;
                    }
                }
            }

            if (json != null && json.has("inheritsFrom")) {
                String parent = json.get("inheritsFrom").getAsString();

                File parentJsonFile = new File(
                        VersionManager.MC_DIR,
                        "versions/" + parent + "/" + parent + ".json"
                );

                if (parentJsonFile.exists()) {
                    JsonObject parentJson = JsonParser
                            .parseReader(new FileReader(parentJsonFile))
                            .getAsJsonObject();

                    return resolveRequiredJavaMajor(parent, parentJson);
                }
            }
        } catch (Exception ex) {
            System.err.println("[JavaRuntime] No se pudo leer javaVersion del JSON: " + ex.getMessage());
        }

        String mc = extractMinecraftVersion(version);

        if (isAtLeast(mc, "1.26")) {
            return 25;
        }

        if (isAtLeast(mc, "1.20.5")) {
            return 21;
        }

        if (isAtLeast(mc, "1.18")) {
            return 17;
        }

        if (isAtLeast(mc, "1.17")) {
            return 16;
        }

        return 8;
    }

    private static String resolveAssetIndex(String version, JsonObject json) {
        String assetId = null;

        try {
            if (json.has("assetIndex") && json.getAsJsonObject("assetIndex").has("id")) {
                assetId = json.getAsJsonObject("assetIndex").get("id").getAsString();
            } else if (json.has("assets")) {
                assetId = json.get("assets").getAsString();
            } else if (json.has("inheritsFrom")) {
                String parent = json.get("inheritsFrom").getAsString();
                assetId = parent;

                File parentJsonFile = new File(
                        VersionManager.MC_DIR,
                        "versions/" + parent + "/" + parent + ".json"
                );

                if (parentJsonFile.exists()) {
                    JsonObject parentJson = JsonParser
                            .parseReader(new FileReader(parentJsonFile))
                            .getAsJsonObject();

                    if (parentJson.has("assetIndex")
                            && parentJson.getAsJsonObject("assetIndex").has("id")) {
                        assetId = parentJson.getAsJsonObject("assetIndex").get("id").getAsString();
                    } else if (parentJson.has("assets")) {
                        assetId = parentJson.get("assets").getAsString();
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[Assets] No se pudo resolver asset index: " + ex.getMessage());
        }

        if (assetId == null || assetId.trim().isEmpty()) {
            assetId = version;
        }

        return assetId;
    }

    private static Thread pipeProcessStream(final InputStream inputStream,
                                            final String prefix,
                                            final StringBuilder gameLog,
                                            final boolean errorStream) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = null;

                try {
                    reader = new BufferedReader(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                    );

                    String line;

                    while ((line = reader.readLine()) != null) {
                        String cleanedLine = cleanGameConsoleLine(line);

                        if (cleanedLine == null || cleanedLine.trim().isEmpty()) {
                            continue;
                        }

                        String finalLine = prefix + cleanedLine;

                        synchronized (gameLog) {
                            gameLog.append(finalLine).append("\n");

                            if (gameLog.length() > 300000) {
                                gameLog.delete(0, gameLog.length() - 300000);
                            }
                        }

                        if (errorStream) {
                            System.err.println(finalLine);
                        } else {
                            System.out.println(finalLine);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (reader != null) {
                            reader.close();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }, errorStream ? "Minecraft-Stderr" : "Minecraft-Stdout");

        thread.setDaemon(true);
        thread.start();

        return thread;
    }

    private static String cleanGameConsoleLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String cleaned = text;

        cleaned = cleaned.replace("\uFEFF", "");
        cleaned = cleaned.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "");
        cleaned = cleaned.replaceAll("\\u001B\\].*?(\\u0007|\\u001B\\\\)", "");
        cleaned = cleaned.replaceAll("\\[[0-9;]*m", "");
        cleaned = cleaned.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        cleaned = cleaned.replaceAll("Â§[0-9A-FK-ORa-fk-or]", "");
        cleaned = cleaned.replace("\uFFFD", "");

        if (looksLikeBinaryGarbage(cleaned)) {
            return "[línea binaria/corrupta omitida]";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);

            if (isAllowedConsoleChar(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }

        return sb.toString().replaceAll(" {3,}", " ").trim();
    }

    private static boolean looksLikeBinaryGarbage(String line) {
        if (line == null) {
            return false;
        }

        String trimmed = line.trim();

        if (trimmed.length() < 24) {
            return false;
        }

        int weird = 0;
        int total = 0;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (Character.isWhitespace(c)) {
                continue;
            }

            total++;

            if (!isAllowedConsoleChar(c)) {
                weird++;
            }
        }

        if (total == 0) {
            return false;
        }

        double ratio = weird / (double) total;

        return ratio > 0.35;
    }

    private static boolean isAllowedConsoleChar(char c) {
        if (c >= 32 && c <= 126) {
            return true;
        }

        if (c == '\t') {
            return true;
        }

        String allowed = "áéíóúÁÉÍÓÚñÑüÜçÇ¿¡€ºª";

        if (allowed.indexOf(c) >= 0) {
            return true;
        }

        String symbols = "·•✓✔✕✖→←↑↓";

        if (symbols.indexOf(c) >= 0) {
            return true;
        }

        return false;
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

    private static boolean isAtLeast(String version, String base) {
        try {
            int[] a = parseVersionParts(version);
            int[] b = parseVersionParts(base);

            int max = Math.max(a.length, b.length);

            for (int i = 0; i < max; i++) {
                int av = i < a.length ? a[i] : 0;
                int bv = i < b.length ? b[i] : 0;

                if (av > bv) {
                    return true;
                }

                if (av < bv) {
                    return false;
                }
            }

            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static int[] parseVersionParts(String version) {
        if (version == null || version.trim().isEmpty()) {
            return new int[]{0};
        }

        String[] split = version.split("\\.");
        int[] result = new int[split.length];

        for (int i = 0; i < split.length; i++) {
            String part = split[i].replaceAll("[^0-9]", "");

            if (part.isEmpty()) {
                result[i] = 0;
            } else {
                result[i] = Integer.parseInt(part);
            }
        }

        return result;
    }
}