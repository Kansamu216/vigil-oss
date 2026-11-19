package io.github.kansamu216.vigil.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端一次上报的内容：事实集合，不含最终结论。
 *
 * <p>必须绑定当次挑战的 nonce，否则整份报告作废；字段全部有上限。</p>
 */
public final class ClientReport {

    private final int protocolVersion;
    private final String nonce;
    private final String playerId;
    private final String playerName;
    private final List<ModEntry> mods;
    private final List<Observation> observations;
    private final String fingerprintHash;
    private final long collectedAtMillis;

    public ClientReport(
            int protocolVersion,
            String nonce,
            String playerId,
            String playerName,
            List<ModEntry> mods,
            List<Observation> observations,
            String fingerprintHash,
            long collectedAtMillis) {
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("invalid protocol version");
        }
        if (nonce == null || nonce.length() < 16 || nonce.length() > 128) {
            throw new IllegalArgumentException("invalid nonce");
        }
        if (playerId == null || playerId.isEmpty() || playerId.length() > 64) {
            throw new IllegalArgumentException("invalid player id");
        }
        if (playerName == null || playerName.length() > 32) {
            throw new IllegalArgumentException("invalid player name");
        }
        if (mods.size() > 2048) {
            throw new IllegalArgumentException("too many mods: " + mods.size());
        }
        if (observations.size() > 256) {
            throw new IllegalArgumentException("too many observations: " + observations.size());
        }
        if (fingerprintHash == null || fingerprintHash.length() != 64) {
            throw new IllegalArgumentException("fingerprint must be 64 hex chars");
        }
        this.protocolVersion = protocolVersion;
        this.nonce = nonce;
        this.playerId = playerId;
        this.playerName = playerName;
        this.mods = Collections.unmodifiableList(new ArrayList<ModEntry>(mods));
        this.observations = Collections.unmodifiableList(new ArrayList<Observation>(observations));
        this.fingerprintHash = fingerprintHash.toLowerCase(java.util.Locale.ROOT);
        this.collectedAtMillis = collectedAtMillis;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String nonce() {
        return nonce;
    }

    public String playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public List<ModEntry> mods() {
        return mods;
    }

    public List<Observation> observations() {
        return observations;
    }

    public String fingerprintHash() {
        return fingerprintHash;
    }

    public long collectedAtMillis() {
        return collectedAtMillis;
    }
}
