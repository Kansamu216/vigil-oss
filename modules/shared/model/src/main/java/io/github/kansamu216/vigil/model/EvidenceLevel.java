package io.github.kansamu216.vigil.model;

/**
 * 证据级别。
 *
 * <p>处置门槛按级别判断，观测级不触发自动封禁。</p>
 */
public enum EvidenceLevel {

    /** 无证据或采集失败。 */
    NONE(0),

    /** 单条事实。 */
    OBSERVATION(1),

    /** 多条独立事实互相印证，可人工处置。 */
    CORROBORATED(2),

    /** 确证级：签名/哈希/完整性证明等不可解释的强证据。 */
    CONFIRMED(3);

    private final int rank;

    EvidenceLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(EvidenceLevel other) {
        return rank >= other.rank;
    }
}
