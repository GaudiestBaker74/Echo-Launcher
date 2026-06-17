package launcher;

// All Platform Support

import java.awt.Desktop;
import java.io.File;

public class PlatformManager {

    public enum OS {
        WINDOWS,
        LINUX,
        MAC,
        UNKNOWN
    }

    public static OS getOS() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            return OS.WINDOWS;
        }

        if (os.contains("mac") || os.contains("darwin")) {
            return OS.MAC;
        }

        if (os.contains("linux") || os.contains("unix")) {
            return OS.LINUX;
        }

        return OS.UNKNOWN;
    }

    public static boolean isWindows() {
        return getOS() == OS.WINDOWS;
    }

    public static boolean isLinux() {
        return getOS() == OS.LINUX;
    }

    public static boolean isMac() {
        return getOS() == OS.MAC;
    }

    public static String getJavaExecutableName() {
        return isWindows() ? "java.exe" : "java";
    }

    public static String getNativeClassifierOSName() {
        if (isWindows()) {
            return "windows";
        }

        if (isMac()) {
            return "osx";
        }

        if (isLinux()) {
            return "linux";
        }

        return "windows";
    }

    public static String getAdoptiumOSName() {
        if (isWindows()) {
            return "windows";
        }

        if (isMac()) {
            return "mac";
        }

        return "linux";
    }

    public static String getAdoptiumArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();

        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }

        if (arch.contains("x86_64") || arch.contains("amd64")) {
            return "x64";
        }

        if (arch.equals("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x32";
        }

        return "x64";
    }

    public static File getDefaultMinecraftDir() {
        String home = System.getProperty("user.home");

        if (isWindows()) {
            String appData = System.getenv("APPDATA");

            if (appData != null && !appData.trim().isEmpty()) {
                return new File(appData, ".minecraft");
            }

            return new File(home, "AppData/Roaming/.minecraft");
        }

        if (isMac()) {
            return new File(home, "Library/Application Support/minecraft");
        }

        return new File(home, ".minecraft");
    }

    public static File getLauncherDataDir() {
        String home = System.getProperty("user.home");

        if (isWindows()) {
            return new File(home, ".minecraft-launcher");
        }

        if (isMac()) {
            return new File(home, "Library/Application Support/MinecraftLauncher");
        }

        return new File(home, ".minecraft-launcher");
    }

    public static void openFolder(File folder) throws Exception {
        if (folder == null) {
            return;
        }

        if (!folder.exists()) {
            folder.mkdirs();
        }

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(folder);
            return;
        }

        if (isLinux()) {
            new ProcessBuilder("xdg-open", folder.getAbsolutePath()).start();
        } else if (isMac()) {
            new ProcessBuilder("open", folder.getAbsolutePath()).start();
        } else if (isWindows()) {
            new ProcessBuilder("explorer", folder.getAbsolutePath()).start();
        }
    }
}