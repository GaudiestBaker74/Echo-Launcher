package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProfileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File getProfilesFile() {
        File dir = new File(System.getProperty("user.home"), ".minecraft-launcher");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(dir, "profiles.json");
    }

    public static List<Profile> loadProfiles() {
        File file = getProfilesFile();

        if (!file.exists()) {
            return new ArrayList<Profile>();
        }

        InputStreamReader reader = null;

        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);

            Type type = new TypeToken<List<Profile>>() {
            }.getType();

            List<Profile> profiles = GSON.fromJson(reader, type);

            if (profiles == null) {
                return new ArrayList<Profile>();
            }

            return profiles;
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ArrayList<Profile>();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void saveProfiles(List<Profile> profiles) throws Exception {
        File file = getProfilesFile();

        OutputStreamWriter writer = null;

        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            GSON.toJson(profiles, writer);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}