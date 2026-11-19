package io.github.kansamu216.vigil.model;

/**
 * 证据级别。
 *
 * <p>处置门槛只认级别，不认“看起来像作弊”：观测级永远不能触发自动封禁。</p>
 */
public enum EvidenceLevel {

    /** 无证据或采集失败。采集失败不等于清白，也不等于作弊。 */
    NONE(0),

    /** 单条事实，可能是正常玩家行为或环境差异。 */
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
