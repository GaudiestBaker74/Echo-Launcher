package launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PreLaunchChecker {

    public static class PreLaunchIssue {
        public String severity;
        public String title;
        public String description;
        public String solution;

        public PreLaunchIssue(String severity, String title, String description, String solution) {
            this.severity = severity;
            this.title = title;
            this.description = description;
            this.solution = solution;
        }
    }

    public static List<PreLaunchIssue> check(Instance instance, String versionId) {
        List<PreLaunchIssue> issues = new ArrayList<PreLaunchIssue>();

        if (instance == null) {
            issues.add(new PreLaunchIssue(
                    "ERROR",
                    "No instance selected",
                    "No active instance was found.",
                    "Select or create an instance before launching."
            ));

            return issues;
        }

        if (versionId == null || versionId.trim().isEmpty()) {
            issues.add(new PreLaunchIssue(
                    "ERROR",
                    "No version selected",
                    "The instance does not have a Minecraft version selected.",
                    "Select a Minecraft version before launching."
            ));
        }

        File gameDir = InstanceManager.getGameDir(instance);
        File modsDir = new File(gameDir, "mods");
        File shaderpacksDir = new File(gameDir, "shaderpacks");

        checkMods(issues, modsDir, versionId);
        checkShaders(issues, modsDir, shaderpacksDir);
        checkFabricBasics(issues, versionId, modsDir);

        return issues;
    }

    private static void checkMods(List<PreLaunchIssue> issues, File modsDir, String versionId) {
        if (modsDir == null || !modsDir.exists()) {
            return;
        }

        File[] files = modsDir.listFiles();

        if (files == null) {
            return;
        }

        List<File> activeMods = new ArrayList<File>();
        List<File> disabledMods = new ArrayList<File>();

        for (File file : files) {
            String lower = file.getName().toLowerCase();

            if (lower.endsWith(".jar")) {
                activeMods.add(file);
            } else if (lower.endsWith(".disabled") || lower.endsWith(".jar.disabled")) {
                disabledMods.add(file);
            }
        }

        if (!disabledMods.isEmpty()) {
            issues.add(new PreLaunchIssue(
                    "INFO",
                    "Disabled mods detected",
                    "There are " + disabledMods.size() + " disabled mods in this instance.",
                    "This is normal if you intentionally disabled them."
            ));
        }

        checkDuplicateMods(issues, activeMods);
        checkVersionMismatchByFilename(issues, activeMods, versionId);
    }

    private static void checkDuplicateMods(List<PreLaunchIssue> issues, List<File> activeMods) {
        Set<String> seen = new HashSet<String>();
        Set<String> duplicates = new HashSet<String>();

        for (File file : activeMods) {
            String key = normalizeModName(file.getName());

            if (key.isEmpty()) {
                continue;
            }

            if (seen.contains(key)) {
                duplicates.add(key);
            }

            seen.add(key);
        }

        if (!duplicates.isEmpty()) {
            issues.add(new PreLaunchIssue(
                    "ERROR",
                    "Duplicate mods detected",
                    "Some mods appear to be installed more than once: " + duplicates,
                    "Open the Mods panel and remove or disable duplicate versions."
            ));
        }
    }

    private static void checkVersionMismatchByFilename(List<PreLaunchIssue> issues, List<File> activeMods, String versionId) {
        String mcVersion = extractMinecraftVersion(versionId);

        if (mcVersion == null || mcVersion.trim().isEmpty()) {
            return;
        }

        for (File file : activeMods) {
            String name = file.getName().toLowerCase();

            String detected = detectMinecraftVersionInFileName(name);

            if (detected == null || detected.trim().isEmpty()) {
                continue;
            }

            if (!detected.equals(mcVersion)) {
                issues.add(new PreLaunchIssue(
                        "WARNING",
                        "Possible mod version mismatch",
                        "The file '" + file.getName() + "' looks like it is for Minecraft " + detected + ", but this instance uses " + mcVersion + ".",
                        "Download the mod version that matches your Minecraft version or disable this mod."
                ));
            }
        }
    }

    private static void checkShaders(List<PreLaunchIssue> issues, File modsDir, File shaderpacksDir) {
        if (shaderpacksDir == null || !shaderpacksDir.exists()) {
            return;
        }

        File[] shaders = shaderpacksDir.listFiles();

        if (shaders == null || shaders.length == 0) {
            return;
        }

        boolean hasShaderZip = false;

        for (File f : shaders) {
            String lower = f.getName().toLowerCase();

            if (lower.endsWith(".zip")) {
                hasShaderZip = true;
                break;
            }
        }

        if (!hasShaderZip) {
            return;
        }

        if (!hasMod(modsDir, "iris")) {
            issues.add(new PreLaunchIssue(
                    "WARNING",
                    "Shaders installed but Iris is missing",
                    "This instance has shader packs installed but Iris Shaders was not detected.",
                    "Install Iris Shaders from the content search or the graphics pack."
            ));
        }
    }

    private static void checkFabricBasics(List<PreLaunchIssue> issues, String versionId, File modsDir) {
        if (versionId == null) {
            return;
        }

        String lowerVersion = versionId.toLowerCase();

        if (!lowerVersion.contains("fabric")) {
            return;
        }

        if (!hasMod(modsDir, "fabric-api")) {
            issues.add(new PreLaunchIssue(
                    "WARNING",
                    "Fabric API not detected",
                    "This is a Fabric instance but Fabric API was not found in the mods folder.",
                    "Many Fabric mods require Fabric API. Install it from the content search."
            ));
        }

        if (hasMod(modsDir, "iris") && !hasMod(modsDir, "sodium")) {
            issues.add(new PreLaunchIssue(
                    "WARNING",
                    "Iris installed without Sodium",
                    "Iris Shaders usually requires Sodium.",
                    "Install Sodium or use the graphics pack installer."
            ));
        }
    }

    private static boolean hasMod(File modsDir, String keyword) {
        if (modsDir == null || !modsDir.exists()) {
            return false;
        }

        File[] files = modsDir.listFiles();

        if (files == null) {
            return false;
        }

        String key = keyword.toLowerCase();

        for (File file : files) {
            String name = file.getName().toLowerCase();

            if (name.endsWith(".jar") && name.contains(key)) {
                return true;
            }
        }

        return false;
    }

    private static String normalizeModName(String fileName) {
        if (fileName == null) {
            return "";
        }

        String name = fileName.toLowerCase();

        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }

        name = name.replaceAll("\\+mc\\d+\\.\\d+(\\.\\d+)?", "");
        name = name.replaceAll("mc\\d+\\.\\d+(\\.\\d+)?", "");
        name = name.replaceAll("\\d+\\.\\d+(\\.\\d+)?", "");
        name = name.replaceAll("fabric", "");
        name = name.replaceAll("forge", "");
        name = name.replaceAll("quilt", "");
        name = name.replaceAll("neoforge", "");
        name = name.replaceAll("[^a-z0-9]+", "-");
        name = name.replaceAll("-+", "-");
        name = name.replaceAll("^-|-$", "");

        return name;
    }

    private static String detectMinecraftVersionInFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(1\\.\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);

        String last = null;

        while (matcher.find()) {
            last = matcher.group(1);
        }

        return last;
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
}