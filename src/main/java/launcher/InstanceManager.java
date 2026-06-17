package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class InstanceManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static File getBaseDir() {
        File dir = new File(PlatformManager.getLauncherDataDir(), "instances");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    public static List<Instance> loadInstances() {
        List<Instance> instances = new ArrayList<Instance>();

        File base = getBaseDir();
        File[] dirs = base.listFiles();

        if (dirs == null) {
            return instances;
        }

        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }

            File json = new File(dir, "instance.json");

            if (!json.exists()) {
                continue;
            }

            InputStreamReader reader = null;

            try {
                reader = new InputStreamReader(new FileInputStream(json), StandardCharsets.UTF_8);
                Instance instance = GSON.fromJson(reader, Instance.class);

                if (instance != null) {
                    if (instance.gameDirPath == null || instance.gameDirPath.trim().isEmpty()) {
                        instance.gameDirPath = new File(dir, "minecraft").getAbsolutePath();
                    }

                    ensureInstanceFolders(instance);
                    instances.add(instance);
                }
            } catch (Exception ex) {
                System.err
                        .println("[InstanceManager] Error reading instance " + dir.getName() + ": " + ex.getMessage());
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return instances;
    }

    public static Instance createDefaultInstance() throws Exception {
        File dir = new File(getBaseDir(), "Main");
        File gameDir = new File(dir, "minecraft");

        Instance instance = new Instance(
                "Main",
                "Steve",
                "",
                "vanilla",
                2,
                gameDir.getAbsolutePath());

        ensureInstanceFolders(instance);
        saveInstance(instance);

        return instance;
    }

    public static Instance createInstance(String name, String username, String version, String type, int ram)
            throws Exception {
        String safe = safeName(name);

        File dir = new File(getBaseDir(), safe);
        File gameDir = new File(dir, "minecraft");

        if (dir.exists()) {
            throw new Exception("An instance with this name already exists.");
        }

        Instance instance = new Instance(
                name,
                username,
                version,
                type,
                ram,
                gameDir.getAbsolutePath());

        ensureInstanceFolders(instance);
        saveInstance(instance);

        return instance;
    }

    public static Instance duplicateInstance(Instance source, String newName) throws Exception {
        if (source == null) {
            throw new Exception("There is no instance selected.");
        }

        Instance copy = createInstance(
                newName,
                source.username,
                source.version,
                source.type,
                source.ram);

        copy.icon = source.icon;
        copy.notes = source.notes;

        copyDirectory(new File(source.gameDirPath), new File(copy.gameDirPath));

        saveInstance(copy);

        return copy;
    }

    public static void saveInstance(Instance instance) throws Exception {
        if (instance == null) {
            return;
        }

        File instanceDir = getInstanceDir(instance);
        instanceDir.mkdirs();

        ensureInstanceFolders(instance);

        File json = new File(instanceDir, "instance.json");

        OutputStreamWriter writer = null;

        try {
            writer = new OutputStreamWriter(new FileOutputStream(json), StandardCharsets.UTF_8);
            GSON.toJson(instance, writer);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void deleteInstance(Instance instance) throws Exception {
        if (instance == null) {
            return;
        }

        File dir = getInstanceDir(instance);
        deleteDirectory(dir);
    }

    public static File getInstanceDir(Instance instance) {
        String safe = safeName(instance.name);
        return new File(getBaseDir(), safe);
    }

    public static File getGameDir(Instance instance) {
        if (instance == null || instance.gameDirPath == null || instance.gameDirPath.trim().isEmpty()) {
            return new File(getBaseDir(), "Principal/minecraft");
        }

        return new File(instance.gameDirPath);
    }

    public static void ensureInstanceFolders(Instance instance) {
        File gameDir = getGameDir(instance);

        new File(gameDir, "mods").mkdirs();
        new File(gameDir, "resourcepacks").mkdirs();
        new File(gameDir, "shaderpacks").mkdirs();
        new File(gameDir, "config").mkdirs();
        new File(gameDir, "saves").mkdirs();
        new File(gameDir, "logs").mkdirs();
    }

    public static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Instance";
        }

        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void copyDirectory(File source, File target) throws Exception {
        if (source == null || !source.exists()) {
            return;
        }

        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }

            File[] children = source.listFiles();

            if (children != null) {
                for (File child : children) {
                    copyDirectory(child, new File(target, child.getName()));
                }
            }
        } else {
            java.nio.file.Files.copy(
                    source.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

        file.delete();
    }

    public static void exportInstance(Instance instance, File zipFile) throws Exception {
        if (instance == null) {
            throw new Exception("There is no instance selected.");
        }

        if (zipFile == null) {
            throw new Exception("Invalid ZIP file.");
        }

        File instanceDir = getInstanceDir(instance);

        if (!instanceDir.exists()) {
            throw new Exception("Instance folder does not exist.");
        }

        File parent = zipFile.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (zipFile.exists()) {
            zipFile.delete();
        }

        ZipOutputStream zos = null;

        try {
            zos = new ZipOutputStream(new FileOutputStream(zipFile));

            addFileToZip(zos, new File(instanceDir, "instance.json"), "instance.json");

            File gameDir = getGameDir(instance);

            addDirectoryToZipIfExists(zos, new File(gameDir, "mods"), "minecraft/mods");
            addDirectoryToZipIfExists(zos, new File(gameDir, "config"), "minecraft/config");
            addDirectoryToZipIfExists(zos, new File(gameDir, "resourcepacks"), "minecraft/resourcepacks");
            addDirectoryToZipIfExists(zos, new File(gameDir, "shaderpacks"), "minecraft/shaderpacks");
        } finally {
            if (zos != null) {
                zos.close();
            }
        }
    }

    public static Instance importInstance(File zipFile) throws Exception {
        if (zipFile == null || !zipFile.exists()) {
            throw new Exception("Invalid instance ZIP.");
        }

        String baseName = zipFile.getName();

        if (baseName.toLowerCase().endsWith(".zip")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        String finalName = makeUniqueInstanceName(baseName);
        File targetDir = new File(getBaseDir(), safeName(finalName));
        targetDir.mkdirs();

        extractInstanceZip(zipFile, targetDir);

        File json = new File(targetDir, "instance.json");

        Instance instance;

        if (json.exists()) {
            InputStreamReader reader = null;

            try {
                reader = new InputStreamReader(new FileInputStream(json), StandardCharsets.UTF_8);
                instance = GSON.fromJson(reader, Instance.class);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

            if (instance == null) {
                instance = new Instance();
            }
        } else {
            instance = new Instance();
        }

        instance.name = makeUniqueInstanceName(
                instance.name == null || instance.name.trim().isEmpty() ? finalName : instance.name);
        instance.gameDirPath = new File(targetDir, "minecraft").getAbsolutePath();

        ensureInstanceFolders(instance);
        saveInstance(instance);

        return instance;
    }

    private static void addDirectoryToZipIfExists(ZipOutputStream zos, File dir, String zipPath) throws Exception {
        if (dir == null || !dir.exists()) {
            return;
        }

        addDirectoryToZip(zos, dir, zipPath);
    }

    private static void addDirectoryToZip(ZipOutputStream zos, File dir, String zipPath) throws Exception {
        File[] files = dir.listFiles();

        if (files == null || files.length == 0) {
            ZipEntry entry = new ZipEntry(zipPath.endsWith("/") ? zipPath : zipPath + "/");
            zos.putNextEntry(entry);
            zos.closeEntry();
            return;
        }

        for (File file : files) {
            String childPath = zipPath + "/" + file.getName();

            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, childPath);
            } else {
                addFileToZip(zos, file, childPath);
            }
        }
    }

    private static void addFileToZip(ZipOutputStream zos, File file, String zipPath) throws Exception {
        if (file == null || !file.exists() || file.isDirectory()) {
            return;
        }

        ZipEntry entry = new ZipEntry(zipPath.replace("\\", "/"));
        zos.putNextEntry(entry);

        FileInputStream in = null;

        try {
            in = new FileInputStream(file);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                zos.write(buffer, 0, read);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }

        zos.closeEntry();
    }

    private static void extractInstanceZip(File zipFile, File targetDir) throws Exception {
        ZipInputStream zis = null;

        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name == null || name.trim().isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                name = name.replace("\\", "/");

                if (name.startsWith("/") || name.contains("../")) {
                    throw new Exception("ZIP inseguro: " + name);
                }

                /*
                 * If the zip comes with a root folder like:
                 * MyInstance/instance.json
                 * we try to normalize it.
                 */
                name = normalizeImportedZipPath(name);

                if (name == null || name.trim().isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                File outFile = new File(targetDir, name);

                String targetPath = targetDir.getCanonicalPath();
                String outPath = outFile.getCanonicalPath();

                if (!outPath.startsWith(targetPath + File.separator)) {
                    throw new Exception("Unsafe ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();

                    if (parent != null) {
                        parent.mkdirs();
                    }

                    FileOutputStream out = null;

                    try {
                        out = new FileOutputStream(outFile);

                        byte[] buffer = new byte[8192];
                        int read;

                        while ((read = zis.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    } finally {
                        if (out != null) {
                            out.close();
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

    private static String normalizeImportedZipPath(String path) {
        if (path == null) {
            return null;
        }

        path = path.replace("\\", "/");

        if (path.equals("instance.json") || path.startsWith("minecraft/")) {
            return path;
        }

        int slash = path.indexOf("/");

        if (slash >= 0) {
            String withoutRoot = path.substring(slash + 1);

            if (withoutRoot.equals("instance.json") || withoutRoot.startsWith("minecraft/")) {
                return withoutRoot;
            }
        }

        return path;
    }

    private static String makeUniqueInstanceName(String preferredName) {
        if (preferredName == null || preferredName.trim().isEmpty()) {
            preferredName = "Imported Instance";
        }

        String base = preferredName.trim();
        String candidate = base;
        int i = 2;

        while (new File(getBaseDir(), safeName(candidate)).exists()) {
            candidate = base + " " + i;
            i++;
        }

        return candidate;
    }

}