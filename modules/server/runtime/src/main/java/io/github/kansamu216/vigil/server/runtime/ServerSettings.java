package io.github.kansamu216.vigil.server.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * 服务端配置。
 *
 * <p>首次启动写入默认文件；非法数值按范围约束处理。
 * 数据库文件限制在数据目录内。</p>
 */
public final class ServerSettings {

    public static final String FILE_NAME = "server.properties";

    private static final String KEY_CHALLENGE_TTL_SECONDS = "challenge.ttlSeconds";
    private static final String KEY_IDLE_SECONDS = "session.idleSeconds";
    private static final String KEY_AUDIT_RETENTION_DAYS = "audit.retentionDays";
    private static final String KEY_MAX_SESSIONS = "session.maxSessions";
    private static final String KEY_DATABASE_FILE = "database.file";
    private static final String KEY_ALERT_SCORE = "policy.alertScore";
    private static final String KEY_KICK_SCORE = "policy.kickScore";
    private static final String KEY_RESTRICT_SCORE = "policy.restrictScore";
    private static final String KEY_BAN_SCORE = "policy.banScore";
    private static final String KEY_AUTO_BAN = "policy.autoBan";
    private static final String KEY_AUTO_KICK = "policy.autoKick";

    private final Properties properties;
    private final List<String> warnings;

    private ServerSettings(Properties properties, List<String> warnings) {
        this.properties = properties;
        this.warnings = warnings;
    }

    public static ServerSettings load(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        } else {
            properties.putAll(defaults());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Vigil server settings");
            }
        }
        List<String> warnings = new ArrayList<String>();
        return new ServerSettings(properties, warnings);
    }

    private static Properties defaults() {
        Properties defaults = new Properties();
        defaults.setProperty(KEY_CHALLENGE_TTL_SECONDS, "20");
        defaults.setProperty(KEY_IDLE_SECONDS, "120");
        defaults.setProperty(KEY_AUDIT_RETENTION_DAYS, "30");
        defaults.setProperty(KEY_MAX_SESSIONS, "4096");
        defaults.setProperty(KEY_DATABASE_FILE, "vigil.db");
        defaults.setProperty(KEY_ALERT_SCORE, "20");
        defaults.setProperty(KEY_KICK_SCORE, "50");
        defaults.setProperty(KEY_RESTRICT_SCORE, "70");
        defaults.setProperty(KEY_BAN_SCORE, "90");
        defaults.setProperty(KEY_AUTO_BAN, "false");
        defaults.setProperty(KEY_AUTO_KICK, "false");
        return defaults;
    }

    /**
     * 解析为运行时配置；数据目录在调用时确定。
     */
    public ServerConfig toServerConfig(Path dataDirectory) {
        Path database = resolveDatabase(dataDirectory);
        long ttlSeconds = readLong(KEY_CHALLENGE_TTL_SECONDS, 20L, 1L, 120L);
        long idleSeconds = readLong(KEY_IDLE_SECONDS, 120L, 5L, 900L);
        long retentionDays = readLong(KEY_AUDIT_RETENTION_DAYS, 30L, 1L, 365L);
        int maxSessions = (int) readLong(KEY_MAX_SESSIONS, 4096L, 16L, 100_000L);

        int alert = (int) readLong(KEY_ALERT_SCORE, 20L, 0L, 100L);
        int kick = (int) readLong(KEY_KICK_SCORE, 50L, 0L, 100L);
        int restrict = (int) readLong(KEY_RESTRICT_SCORE, 70L, 0L, 100L);
        int ban = (int) readLong(KEY_BAN_SCORE, 90L, 0L, 100L);
        if (kick < alert || restrict < kick || ban < restrict) {
            warnings.add("policy thresholds are not non-decreasing; falling back to defaults");
            alert = 20;
            kick = 50;
            restrict = 70;
            ban = 90;
        }

        return new ServerConfig(
                ttlSeconds * 1000L,
                idleSeconds * 1000L,
                retentionDays * 24L * 60L * 60L * 1000L,
                maxSessions,
                database.toString(),
                new io.github.kansamu216.vigil.server.decision.DecisionPolicy(
                        alert, kick, restrict, ban,
                        readBoolean(KEY_AUTO_BAN, false),
                        readBoolean(KEY_AUTO_KICK, false)));
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    private long readLong(String key, long fallback, long min, long max) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < min || value > max) {
                warnings.add(key + " out of range [" + min + "," + max + "]: " + raw);
                return Math.max(min, Math.min(max, value));
            }
            return value;
        } catch (NumberFormatException e) {
            warnings.add(key + " is not a number, using " + fallback);
            return fallback;
        }
    }

    private boolean readBoolean(String key, boolean fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        warnings.add(key + " is not a boolean, using " + fallback);
        return fallback;
    }

    private Path resolveDatabase(Path dataDirectory) {
        String raw = properties.getProperty(KEY_DATABASE_FILE, "vigil.db").trim();
        Path candidate = Paths.get(raw);
        if (candidate.isAbsolute() || raw.contains("..")) {
            warnings.add(KEY_DATABASE_FILE + " must stay inside the data directory: " + raw);
            return dataDirectory.resolve("vigil.db").toAbsolutePath().normalize();
        }
        Path base = dataDirectory.toAbsolutePath().normalize();
        Path resolved = base.resolve(candidate).normalize();
        if (!resolved.startsWith(base)) {
            warnings.add(KEY_DATABASE_FILE + " escapes the data directory: " + raw);
            return base.resolve("vigil.db");
        }
        return resolved;
    }
}
