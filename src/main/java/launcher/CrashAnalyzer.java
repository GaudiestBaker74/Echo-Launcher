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
            super(crashInfo == null ? "Minecraft crasheó." : crashInfo.title);
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
                    "Java incompatible",
                    "Minecraft o algún mod fue compilado para una versión de Java más nueva que la que se está usando.",
                    "Usa el Java recomendado para esa versión. Si el launcher tiene runtimes automáticos, elimina el runtime descargado corrupto y vuelve a iniciar. Para Minecraft moderno puede requerirse Java 21 o Java 25.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("outofmemoryerror")
                || lower.contains("java heap space")
                || lower.contains("unable to allocate")) {
            return new CrashInfo(
                    "Memoria RAM insuficiente",
                    "Minecraft se quedó sin memoria durante la carga o durante la partida.",
                    "Sube la RAM asignada en el launcher. Para mods normales usa 4-6 GB. Para packs grandes o shaders usa 6-8 GB. Evita asignar toda la RAM del PC.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("modresolutionexception")
                || lower.contains("could not resolve")
                || lower.contains("depends on")
                || lower.contains("requires")
                || lower.contains("requires any version of")
                || lower.contains("but only the wrong version is present")
                || lower.contains("missing required mod")) {
            return new CrashInfo(
                    "Falta una dependencia de mod",
                    "Uno o más mods necesitan otra librería/mod para funcionar.",
                    "Abre el buscador de mods e instala la dependencia indicada en el log. Revisa especialmente Fabric API, Cloth Config, Architectury, Sodium, Indium o Mod Menu.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("fabricloader")
                && (lower.contains("incompatible mod set") || lower.contains("could not find required mod"))) {
            return new CrashInfo(
                    "Mods incompatibles con Fabric",
                    "Fabric detectó que el conjunto de mods no es compatible o que falta un mod requerido.",
                    "Revisa la versión de Minecraft seleccionada y descarga mods compatibles exactamente con esa versión. Desactiva mods sospechosos desde el panel de Mods.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("duplicatemod")
                || lower.contains("duplicate mod")
                || lower.contains("multiple versions of")
                || lower.contains("mod id") && lower.contains("duplicate")) {
            return new CrashInfo(
                    "Mods duplicados",
                    "Hay dos versiones del mismo mod instaladas al mismo tiempo.",
                    "Abre el panel de Mods y elimina o desactiva una de las copias duplicadas.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("mixin apply failed")
                || lower.contains("mixin transformation")
                || lower.contains("injection failure")
                || lower.contains("critical injection failure")) {
            return new CrashInfo(
                    "Error de Mixin",
                    "Un mod intentó modificar clases internas de Minecraft y falló. Suele ser por versión incorrecta o incompatibilidad entre mods.",
                    "Actualiza el mod que aparece en el error. Si empezó después de instalar un mod nuevo, desactívalo. Revisa también Sodium/Iris/Indium y mods visuales.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("glfw error 65542")
                || lower.contains("opengl")
                || lower.contains("pixel format not accelerated")
                || lower.contains("failed to create window")) {
            return new CrashInfo(
                    "Problema gráfico / OpenGL",
                    "Minecraft no pudo iniciar correctamente la ventana gráfica.",
                    "Actualiza los drivers de la tarjeta gráfica. Si usas una GPU antigua, prueba una versión más vieja de Minecraft o desactiva shaders/mods gráficos.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("accessdeniedexception")
                || lower.contains("being used by another process")
                || lower.contains("permission denied")) {
            return new CrashInfo(
                    "Permisos o archivo bloqueado",
                    "El juego no pudo acceder a un archivo porque está bloqueado o no tiene permisos.",
                    "Cierra Minecraft si quedó abierto, cierra editores/antivirus que puedan bloquear archivos y ejecuta el launcher de nuevo.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("failed to download")
                || lower.contains("connection timed out")
                || lower.contains("read timed out")
                || lower.contains("unknownhostexception")
                || lower.contains("sslhandshakeexception")) {
            return new CrashInfo(
                    "Error de descarga o conexión",
                    "Minecraft no pudo descargar o acceder a un recurso necesario.",
                    "Revisa tu conexión a internet, firewall o antivirus. Después usa la opción de reparar instalación si está disponible.",
                    extractImportantLines(log)
            );
        }

        if (lower.contains("no such file")
                || lower.contains("filenotfoundexception")
                || lower.contains("missing")) {
            return new CrashInfo(
                    "Archivo faltante",
                    "Falta un archivo necesario para iniciar Minecraft.",
                    "Repara o vuelve a preparar la versión seleccionada. Si es un mod, reinstálalo desde el buscador.",
                    extractImportantLines(log)
            );
        }

        return new CrashInfo(
                "Minecraft se cerró con error",
                "El juego terminó con código de salida " + exitCode + ", pero no se pudo identificar una causa exacta automáticamente.",
                "Revisa los últimos errores del log. Prueba desactivar mods recientes, reparar la instalación o usar otra versión de Minecraft/Java.",
                extractImportantLines(log)
        );
    }

    private static String extractImportantLines(String log) {
        if (log == null || log.trim().isEmpty()) {
            return "No hay log disponible.";
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