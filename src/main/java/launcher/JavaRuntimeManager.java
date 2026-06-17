package launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JavaRuntimeManager {

    private static final String USER_AGENT = "ProfessionalMinecraftLauncher/1.0";

    public static String getJava(int majorVersion) throws Exception {
        File runtimeDir = getRuntimeDir(majorVersion);

        File existingJava = findJavaExecutable(runtimeDir);

        if (existingJava != null && existingJava.exists()) {
            makeExecutable(existingJava);
            System.out.println("[JavaRuntime] Usando Java " + majorVersion + ": " + existingJava.getAbsolutePath());
            return existingJava.getAbsolutePath();
        }

        System.out.println("[JavaRuntime] Java " + majorVersion + " no encontrado. Descargando runtime...");

        deleteDirectory(runtimeDir);
        runtimeDir.mkdirs();

        downloadAndExtractRuntime(majorVersion, runtimeDir);

        File javaExe = findJavaExecutable(runtimeDir);

        if (javaExe == null || !javaExe.exists()) {
            throw new Exception(
                    "No se pudo encontrar " + PlatformManager.getJavaExecutableName() +
                            " después de descargar Java " + majorVersion +
                            " en " + runtimeDir.getAbsolutePath()
            );
        }

        makeExecutable(javaExe);

        System.out.println("[JavaRuntime] Java " + majorVersion + " instalado: " + javaExe.getAbsolutePath());

        return javaExe.getAbsolutePath();
    }

    private static File getRuntimeDir(int majorVersion) {
        File baseDir = new File(PlatformManager.getLauncherDataDir(), "runtimes");
        return new File(baseDir, "java-" + majorVersion + "-" + PlatformManager.getAdoptiumOSName() + "-" + PlatformManager.getAdoptiumArch());
    }

    private static void downloadAndExtractRuntime(int majorVersion, File runtimeDir) throws Exception {
        runtimeDir.mkdirs();

        File archiveFile = new File(runtimeDir, "runtime.archive");

        if (archiveFile.exists()) {
            archiveFile.delete();
        }

        String url = buildAdoptiumUrl(majorVersion, "jre");

        try {
            downloadFile(url, archiveFile);
        } catch (Exception jreError) {
            System.err.println("[JavaRuntime] No se pudo descargar JRE. Probando JDK. Error: " + jreError.getMessage());

            url = buildAdoptiumUrl(majorVersion, "jdk");
            downloadFile(url, archiveFile);
        }

        extractArchive(archiveFile, runtimeDir);

        archiveFile.delete();
    }

    private static String buildAdoptiumUrl(int majorVersion, String imageType) {
        String os = PlatformManager.getAdoptiumOSName();
        String arch = PlatformManager.getAdoptiumArch();

        return "https://api.adoptium.net/v3/binary/latest/"
                + majorVersion
                + "/ga/"
                + os
                + "/"
                + arch
                + "/"
                + imageType
                + "/hotspot/normal/eclipse";
    }

    private static void downloadFile(String urlStr, File targetFile) throws Exception {
        HttpURLConnection conn = openConnectionFollowingRedirects(urlStr);

        InputStream in = null;
        FileOutputStream out = null;

        try {
            int code = conn.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " descargando runtime Java");
            }

            long total = conn.getContentLengthLong();

            in = conn.getInputStream();
            out = new FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0;
            long lastPrint = 0;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;

                long now = System.currentTimeMillis();

                if (now - lastPrint > 1000) {
                    lastPrint = now;

                    if (total > 0) {
                        int percent = (int) ((downloaded * 100) / total);
                        System.out.println("[JavaRuntime] Descargando Java... " + percent + "%");
                    } else {
                        System.out.println("[JavaRuntime] Descargando Java... " + downloaded / 1024 / 1024 + " MB");
                    }
                }
            }
        } finally {
            if (out != null) {
                out.close();
            }

            if (in != null) {
                in.close();
            }

            conn.disconnect();
        }

        if (!targetFile.exists() || targetFile.length() == 0) {
            throw new Exception("La descarga del runtime Java quedó vacía.");
        }
    }

    private static HttpURLConnection openConnectionFollowingRedirects(String urlStr) throws Exception {
        String current = urlStr;

        for (int i = 0; i < 10; i++) {
            URL url = URI.create(current).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(false);

            int code = conn.getResponseCode();

            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();

                if (location == null || location.trim().isEmpty()) {
                    throw new Exception("Redirect sin Location descargando Java.");
                }

                current = location;
                continue;
            }

            return conn;
        }

        throw new Exception("Demasiados redirects descargando Java.");
    }

    private static void extractArchive(File archiveFile, File targetDir) throws Exception {
        /*
         * Adoptium returns .zip for Windows and .tar.gz for Linux/macOS.
         * We saved it without extension, so detect by OS.
         */
        if (PlatformManager.isWindows()) {
            extractZip(archiveFile, targetDir);
        } else {
            extractTarGz(archiveFile, targetDir);
        }
    }

    private static void extractZip(File zipFile, File targetDir) throws Exception {
        ZipInputStream zis = null;

        try {
            zis = new ZipInputStream(new java.io.FileInputStream(zipFile));

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(targetDir, entry.getName());

                String targetPath = targetDir.getCanonicalPath();
                String outPath = outFile.getCanonicalPath();

                if (!outPath.startsWith(targetPath + File.separator)) {
                    throw new Exception("Entrada ZIP insegura: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();

                    if (parent != null) {
                        parent.mkdirs();
                    }

                    FileOutputStream fos = null;

                    try {
                        fos = new FileOutputStream(outFile);

                        byte[] buffer = new byte[8192];
                        int len;

                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    } finally {
                        if (fos != null) {
                            fos.close();
                        }
                    }
                }

                zis.closeEntry();
            }
        } finally {
            if (zis != null) {
                zis.close();
            }
        }
    }

    private static void extractTarGz(File archiveFile, File targetDir) throws Exception {
        /*
         * Use system tar on Linux/macOS.
         * This is much simpler than implementing TAR manually.
         */
        ProcessBuilder pb = new ProcessBuilder(
                "tar",
                "-xzf",
                archiveFile.getAbsolutePath(),
                "-C",
                targetDir.getAbsolutePath()
        );

        pb.redirectErrorStream(true);

        Process process = pb.start();

        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
        );

        try {
            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println("[JavaRuntime/tar] " + line);
            }
        } finally {
            reader.close();
        }

        int exit = process.waitFor();

        if (exit != 0) {
            throw new Exception("No se pudo extraer runtime Java con tar. Código: " + exit);
        }
    }

    private static File findJavaExecutable(File dir) {
        if (dir == null || !dir.exists()) {
            return null;
        }

        String javaName = PlatformManager.getJavaExecutableName();

        File direct = new File(dir, "bin/" + javaName);

        if (direct.exists()) {
            return direct;
        }

        return findJavaExecutableRecursive(dir, javaName);
    }

    private static File findJavaExecutableRecursive(File dir, String javaName) {
        File[] files = dir.listFiles();

        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findJavaExecutableRecursive(file, javaName);

                if (found != null) {
                    return found;
                }
            } else if (file.getName().equalsIgnoreCase(javaName)
                    && file.getParentFile() != null
                    && file.getParentFile().getName().equalsIgnoreCase("bin")) {
                return file;
            }
        }

        return null;
    }

    private static void makeExecutable(File file) {
        try {
            if (file != null && file.exists() && !PlatformManager.isWindows()) {
                file.setExecutable(true);
            }
        } catch (Exception ignored) {
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
            System.err.println("[JavaRuntime] No se pudo borrar: " + file.getAbsolutePath());
        }
    }
}