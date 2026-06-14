package launcher;

public class Profile {
    public String name;
    public String username;
    public int ram;
    public String version;
    public String type;

    public String skinPath;
    public String capePath;

    public Profile() {
    }

    public Profile(String name, String username, int ram, String version, String type) {
        this.name = name;
        this.username = username;
        this.ram = ram;
        this.version = version;
        this.type = type;
    }

    public Profile(String name, String username, int ram, String version, String type, String skinPath, String capePath) {
        this.name = name;
        this.username = username;
        this.ram = ram;
        this.version = version;
        this.type = type;
        this.skinPath = skinPath;
        this.capePath = capePath;
    }

    @Override
    public String toString() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }

        return username + " · " + version + " · " + ram + "GB";
    }
}