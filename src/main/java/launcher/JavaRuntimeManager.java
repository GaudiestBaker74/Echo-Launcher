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
            System.out.println("[JavaRuntime] Usando Java " + majorVersion + ": " + existingJava.getAbsolutePath());
            return existingJava.getAbsolutePath();
        }

        System.out.println("[JavaRuntime] Java " + majorVersion + " no encontrado. Descargando runtime...");

        downloadAndExtractRuntime(majorVersion, runtimeDir);

        File javaExe = findJavaExecutable(runtimeDir);

        if (javaExe == null || !javaExe.exists()) {
            throw new Exception("No se pudo encontrar java.exe después de descargar Java " + majorVersion);
        }

        System.out.println("[JavaRuntime] Java " + majorVersion + " instalado: " + javaExe.getAbsolutePath());

        return javaExe.getAbsolutePath();
    }

    private static File getRuntimeDir(int majorVersion) {
        File baseDir = new File(PlatformManager.getLauncherDataDir(), "runtimes");
        return new File(baseDir, "java-" + majorVersion);
    }

    private static void downloadAndExtractRuntime(int majorVersion, File runtimeDir) throws Exception {
        runtimeDir.mkdirs();

        File zipFile = new File(runtimeDir, "runtime.zip");

        if (zipFile.exists()) {
            zipFile.delete();
        }

        String url = buildAdoptiumUrl(majorVersion, "jre");

        try {
            downloadFile(url, zipFile);
        } catch (Exception jreError) {
            System.err.println("[JavaRuntime] No se pudo descargar JRE. Probando JDK. Error: " + jreError.getMessage());

            url = buildAdoptiumUrl(majorVersion, "jdk");
            downloadFile(url, zipFile);
        }

        extractZip(zipFile, runtimeDir);

        zipFile.delete();
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

    private static String getAdoptiumOs() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return "windows";
        }

        if (os.contains("mac")) {
            return "mac";
        }

        return "linux";
    }

    private static String getAdoptiumArch() {
        String arch = System.getProperty("os.arch").toLowerCase();

        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }

        return "x64";
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

        for (int i = 0; i < 8; i++) {
            URL url = URI.create(current).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
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

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}