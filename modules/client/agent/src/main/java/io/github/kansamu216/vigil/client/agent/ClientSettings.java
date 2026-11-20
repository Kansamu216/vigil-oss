package io.github.kansamu216.vigil.client.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 客户端配置。
 *
 * <p>信任锚必须由部署者明确填写；留空表示“尚未配置”，客户端不会自动接受
 * 首次见到的服务器公钥。指纹格式非法时按未配置处理并记录原因。</p>
 */
public final class ClientSettings {

    public static final String FILE_NAME = "client.properties";
    private static final String KEY_ENABLED = "trust.enabled";
    private static final String KEY_FINGERPRINT = "trust.fingerprint";

    private final boolean enabled;
    private final String fingerprint;
    private final String problem;

    private ClientSettings(boolean enabled, String fingerprint, String problem) {
        this.enabled = enabled;
        this.fingerprint = fingerprint;
        this.problem = problem;
    }

    public static ClientSettings load(Path file) throws IOException {
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
            properties.setProperty(KEY_ENABLED, "true");
            properties.setProperty(KEY_FINGERPRINT, "");
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Vigil client settings: set trust.fingerprint to the server key fingerprint");
            }
        }

        boolean enabled = !"false".equalsIgnoreCase(properties.getProperty(KEY_ENABLED, "true").trim());
        String raw = properties.getProperty(KEY_FINGERPRINT, "").trim();
        if (raw.isEmpty()) {
            return new ClientSettings(enabled, "", "trust.fingerprint is empty");
        }
        String normalized = raw.toLowerCase(java.util.Locale.ROOT);
        if (!isValidFingerprint(normalized)) {
            return new ClientSettings(enabled, "", "trust.fingerprint must be 64 hex characters");
        }
        return new ClientSettings(enabled, normalized, null);
    }

    public static boolean isValidFingerprint(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean hasTrustAnchor() {
        return enabled && !fingerprint.isEmpty();
    }

    public String fingerprint() {
        return fingerprint;
    }

    /** 未配置或格式非法时的原因，用于启动日志；没有问题时返回 null。 */
    public String problem() {
        return problem;
    }
}
