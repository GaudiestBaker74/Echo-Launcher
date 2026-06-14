package launcher;


public class VersionEntry {
    public final String id;


    public VersionEntry(String id) {
        this.id = id;
    }


    @Override
    public String toString() {
        return id;
    }
}
