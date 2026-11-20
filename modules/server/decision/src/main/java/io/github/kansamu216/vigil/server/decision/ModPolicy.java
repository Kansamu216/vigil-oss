package io.github.kansamu216.vigil.server.decision;

import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.ModEntry;
import io.github.kansamu216.vigil.model.Observation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 服务端 Mod 白名单。
 *
 * <p>每行格式：{@code modId [version] [sha256]}，省略项或 {@code *} 表示不限制。
 * 白名单只用于产生观测与告警，不产生确证级证据，也不会触发自动封禁。</p>
 */
public final class ModPolicy {

    private static final String WILDCARD = "*";
    private static final int MAX_RULES = 2048;
    private static final int MAX_DETAIL_IDS = 4;

    private final Map<String, Rule> rules;
    private final boolean enabled;

    private ModPolicy(Map<String, Rule> rules, boolean enabled) {
        this.rules = rules;
        this.enabled = enabled;
    }

    public static ModPolicy disabled() {
        return new ModPolicy(Collections.<String, Rule>emptyMap(), false);
    }

    public static ModPolicy loadIfExists(Path file) throws IOException {
        if (file == null || !Files.exists(file)) {
            return disabled();
        }
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public static ModPolicy parse(List<String> lines) {
        if (lines == null) {
            throw new IllegalArgumentException("policy lines are required");
        }
        Map<String, Rule> parsed = new LinkedHashMap<String, Rule>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw;
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length > 3) {
                throw new IllegalArgumentException("invalid mod policy line: " + raw);
            }
            String id = parts[0].toLowerCase(Locale.ROOT);
            if (id.isEmpty() || id.length() > 128) {
                throw new IllegalArgumentException("invalid mod id: " + parts[0]);
            }
            String version = parts.length >= 2 ? parts[1] : WILDCARD;
            String hash = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : WILDCARD;
            if (version.length() > 64) {
                throw new IllegalArgumentException("mod version too long: " + version);
            }
            if (!WILDCARD.equals(hash) && !hash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid mod hash for " + id);
            }
            if (parsed.put(id, new Rule(version, hash)) != null) {
                throw new IllegalArgumentException("duplicate mod rule: " + id);
            }
            if (parsed.size() > MAX_RULES) {
                throw new IllegalArgumentException("too many mod rules");
            }
        }
        if (parsed.isEmpty()) {
            return disabled();
        }
        return new ModPolicy(Collections.unmodifiableMap(parsed), true);
    }

    public boolean enabled() {
        return enabled;
    }

    public int ruleCount() {
        return rules.size();
    }

    public Result evaluate(List<ModEntry> mods) {
        return evaluate(mods, true);
    }

    public static boolean inventoryComplete(ClientReport report) {
        for (Observation observation : report.observations()) {
            String code = observation.code();
            if (code.startsWith("COLLECT_MOD_") || "COLLECT_REPORT_TRUNCATED".equals(code)) {
                return false;
            }
        }
        return true;
    }

    public Result evaluate(List<ModEntry> mods, boolean inventoryComplete) {
        if (!enabled) {
            return Result.empty();
        }
        int unknown = 0;
        int versionMismatch = 0;
        int hashMismatch = 0;
        List<String> unknownIds = new ArrayList<String>();
        List<String> versionIds = new ArrayList<String>();
        List<String> hashIds = new ArrayList<String>();

        for (ModEntry mod : mods) {
            Rule rule = rules.get(mod.id().toLowerCase(Locale.ROOT));
            if (rule == null) {
                unknown++;
                addId(unknownIds, mod.id());
                continue;
            }
            if (!WILDCARD.equals(rule.version) && !rule.version.equals(mod.version())) {
                versionMismatch++;
                addId(versionIds, mod.id());
            }
            if (!WILDCARD.equals(rule.sha256) && !rule.sha256.equals(mod.sha256())) {
                hashMismatch++;
                addId(hashIds, mod.id());
            }
        }

        List<Observation> observations = new ArrayList<Observation>();
        if (mods.isEmpty() && inventoryComplete) {
            observations.add(new Observation("MOD_POLICY_EMPTY", "no mod list", 40));
        }
        if (unknown > 0) {
            observations.add(new Observation(
                    "MOD_POLICY_UNKNOWN", detail(unknown, unknownIds), 40));
        }
        if (versionMismatch > 0) {
            observations.add(new Observation(
                    "MOD_POLICY_VERSION", detail(versionMismatch, versionIds), 50));
        }
        if (hashMismatch > 0) {
            observations.add(new Observation(
                    "MOD_POLICY_HASH", detail(hashMismatch, hashIds), 60));
        }
        if (observations.isEmpty()) {
            return Result.empty();
        }
        return new Result(
                observations,
                "unknown=" + unknown + " version=" + versionMismatch + " hash=" + hashMismatch);
    }

    private static void addId(List<String> ids, String id) {
        if (ids.size() < MAX_DETAIL_IDS) {
            ids.add(id);
        }
    }

    private static String detail(int count, List<String> ids) {
        StringBuilder builder = new StringBuilder("count=").append(count);
        if (!ids.isEmpty()) {
            builder.append(" ids=");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(ids.get(i));
            }
        }
        String text = builder.toString();
        return text.length() <= 256 ? text : text.substring(0, 256);
    }

    private static final class Rule {
        private final String version;
        private final String sha256;

        private Rule(String version, String sha256) {
            this.version = version;
            this.sha256 = sha256;
        }
    }

    public static final class Result {
        private final List<Observation> observations;
        private final String summary;

        private Result(List<Observation> observations, String summary) {
            this.observations = Collections.unmodifiableList(new ArrayList<Observation>(observations));
            this.summary = summary;
        }

        private static Result empty() {
            return new Result(Collections.<Observation>emptyList(), "ok");
        }

        public boolean clean() {
            return observations.isEmpty();
        }

        public List<Observation> observations() {
            return observations;
        }

        public String summary() {
            return summary;
        }
    }
}
