package io.github.kansamu216.vigil.model;

public final class ModEntry {

    private final String id;
    private final String version;
    private final String fileName;
    private final long sizeBytes;
    private final String sha256;

    public ModEntry(String id, String version, String fileName, long sizeBytes, String sha256) {
        if (id == null || id.isEmpty() || id.length() > 128) {
            throw new IllegalArgumentException("invalid mod id");
        }
        if (version == null || version.length() > 64) {
            throw new IllegalArgumentException("invalid mod version");
        }
        if (fileName == null || fileName.length() > 256) {
            throw new IllegalArgumentException("invalid mod file name");
        }
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("negative mod size");
        }
        if (sha256 == null || sha256.length() != 64) {
            throw new IllegalArgumentException("sha256 must be 64 hex chars");
        }
        this.id = id;
        this.version = version;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public String fileName() {
        return fileName;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String sha256() {
        return sha256;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModEntry)) {
            return false;
        }
        ModEntry that = (ModEntry) other;
        return id.equals(that.id)
                && version.equals(that.version)
                && sha256.equals(that.sha256)
                && sizeBytes == that.sizeBytes;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + sha256.hashCode();
        result = 31 * result + (int) (sizeBytes ^ (sizeBytes >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return id + "@" + version + " (" + sha256.substring(0, 12) + "…)";
    }
}
