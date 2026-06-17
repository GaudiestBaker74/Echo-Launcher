package launcher;

public class CrashAnalyzer {

    public static class CrashInfo {
        public String title;
        public String cause;
        public String solution;
        public String details;

        public CrashInfo(String title, String cause, String solution, String details) {
            this.title = title;
            this.cause = cause;
            this.solution = solution;
            this.details = details;
        }
    }

    public static class CrashException extends Exception {
        private final CrashInfo crashInfo;
        private final String gameLog;
        private final int exitCode;

        public CrashException(CrashInfo crashInfo, String gameLog, int exitCode) {
            super(crashInfo == null ? "Minecraft crashed." : crashInfo.title);
            this.crashInfo = crashInfo;
            this.gameLog = gameLog;
            this.exitCode = exitCode;
        }

        public CrashInfo getCrashInfo() {
            return crashInfo;
        }

        public String getGameLog() {
            return gameLog;
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    public static CrashInfo analyze(String log, int exitCode) {
        if (log == null) {
            log = "";
        }

        String lower = log.toLowerCase();

        if (lower.contains("unsupportedclassversionerror")
                || lower.contains("has been compiled by a more recent version of the java runtime")
                || lower.contains("class file version")) {
            return new CrashInfo(
                    "Incompatible Java",
                    "Minecraft or a mod was compiled for a newer version of Java than the one being used.",
                    "Use the recommended Java for that version. If the launcher has automatic runtimes, remove the corrupted downloaded runtime and try again. Modern Minecraft may require Java 21 or Java 25.",
                    extractImportantLines(log));
        }

        if (lower.contains("outofmemoryerror")
                || lower.contains("java heap space")
                || lower.contains("unable to allocate")) {
            return new CrashInfo(
                    "Insufficient RAM Memory",
                    "Minecraft ran out of memory during loading or during the game.",
                    "Increase the allocated RAM in the launcher. For normal mods use 4-6 GB. For large packs or shaders use 6-8 GB. Avoid allocating all the PC's RAM.",
                    extractImportantLines(log));
        }

        if (lower.contains("modresolutionexception")
                || lower.contains("could not resolve")
                || lower.contains("depends on")
                || lower.contains("requires")
                || lower.contains("requires any version of")
                || lower.contains("but only the wrong version is present")
                || lower.contains("missing required mod")) {
            return new CrashInfo(
                    "Missing mod dependency",
                    "One or more mods require another library/mod to function.",
                    "Open the mod browser and install the dependency indicated in the log. Check especially Fabric API, Cloth Config, Architectury, Sodium, Indium or Mod Menu.",
                    extractImportantLines(log));
        }

        if (lower.contains("fabricloader")
                && (lower.contains("incompatible mod set") || lower.contains("could not find required mod"))) {
            return new CrashInfo(
                    "Incompatible mods with Fabric",
                    "Fabric detected that the mod set is not compatible or a required mod is missing.",
                    "Check the selected Minecraft version and download mods compatible with exactly that version. Disable suspicious mods from the Mods panel.",
                    extractImportantLines(log));
        }

        if (lower.contains("duplicatemod")
                || lower.contains("duplicate mod")
                || lower.contains("multiple versions of")
                || lower.contains("mod id") && lower.contains("duplicate")) {
            return new CrashInfo(
                    "Duplicate mods",
                    "There are two versions of the same mod installed at the same time.",
                    "Open the Mods panel and remove or disable one of the duplicate copies.",
                    extractImportantLines(log));
        }

        if (lower.contains("mixin apply failed")
                || lower.contains("mixin transformation")
                || lower.contains("injection failure")
                || lower.contains("critical injection failure")) {
            return new CrashInfo(
                    "Mixin Error",
                    "A mod tried to modify Minecraft's internal classes and failed. This is usually due to an incorrect version or incompatibility between mods.",
                    "Update the mod that appears in the error. If it started after installing a new mod, disable it. Also check Sodium/Iris/Indium and visual mods.",
                    extractImportantLines(log));
        }

        if (lower.contains("glfw error 65542")
                || lower.contains("opengl")
                || lower.contains("pixel format not accelerated")
                || lower.contains("failed to create window")) {
            return new CrashInfo(
                    "Graphic/OpenGL Problem",
                    "Minecraft could not start the graphics window correctly.",
                    "Update your graphics card drivers. If you are using an older GPU, try an older version of Minecraft or disable graphics shaders/mods.",
                    extractImportantLines(log));
        }

        if (lower.contains("accessdeniedexception")
                || lower.contains("being used by another process")
                || lower.contains("permission denied")) {
            return new CrashInfo(
                    "Permissions or blocked file",
                    "The game could not access a file because it is blocked or has no permissions.",
                    "Close Minecraft if it remained open, close editors/antiviruses that may block files and run the launcher again.",
                    extractImportantLines(log));
        }

        if (lower.contains("failed to download")
                || lower.contains("connection timed out")
                || lower.contains("read timed out")
                || lower.contains("unknownhostexception")
                || lower.contains("sslhandshakeexception")) {
            return new CrashInfo(
                    "Download or connection error",
                    "Minecraft could not download or access a necessary resource.",
                    "Check your internet connection, firewall or antivirus. Then use the repair installation option if available.",
                    extractImportantLines(log));
        }

        if (lower.contains("no such file")
                || lower.contains("filenotfoundexception")
                || lower.contains("missing")) {
            return new CrashInfo(
                    "Missing file",
                    "A file necessary to start Minecraft is missing.",
                    "Repair or re-prepare the selected version. If it is a mod, reinstall it from the browser.",
                    extractImportantLines(log));
        }

        return new CrashInfo(
                "Minecraft closed with error",
                "The game terminated with exit code " + exitCode
                        + ", but an exact cause could not be identified automatically.",
                "Check the latest errors in the log. Try disabling recent mods, repairing the installation or using another version of Minecraft/Java.",
                extractImportantLines(log));
    }

    private static String extractImportantLines(String log) {
        if (log == null || log.trim().isEmpty()) {
            return "No log available.";
        }

        String[] lines = log.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();

        int added = 0;

        for (String line : lines) {
            String lower = line.toLowerCase();

            if (lower.contains("exception")
                    || lower.contains("error")
                    || lower.contains("caused by")
                    || lower.contains("unsupportedclassversionerror")
                    || lower.contains("modresolutionexception")
                    || lower.contains("mixin")
                    || lower.contains("requires")
                    || lower.contains("missing")
                    || lower.contains("failed")) {
                sb.append(line).append("\n");
                added++;

                if (added >= 25) {
                    break;
                }
            }
        }

        if (sb.length() == 0) {
            int start = Math.max(0, lines.length - 25);

            for (int i = start; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
        }

        return sb.toString().trim();
    }
}