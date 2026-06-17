package launcher;

public class Instance {
    public String name;
    public String username;
    public String version;
    public String type;
    public int ram;
    public String gameDirPath;
    public String icon;
    public String notes;

    public long lastPlayedAt;
    public long totalPlayTimeSeconds;

    public String customClientJarPath;
    public String jvmArgs;

    public Instance() {
    }

    public Instance(String name, String username, String version, String type, int ram, String gameDirPath) {
        this.name = name;
        this.username = username;
        this.version = version;
        this.type = type;
        this.ram = ram;
        this.gameDirPath = gameDirPath;
        this.icon = "🌱";
        this.notes = "";
        this.customClientJarPath = "";
        this.jvmArgs = "";
    }

    @Override
    public String toString() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }

        return "Instancia";
    }
}