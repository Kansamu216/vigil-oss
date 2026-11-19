package io.github.kansamu216.vigil.server.decision;

/**
 * 服务端验证结果。
 *
 * <p>这是判定层唯一的“强证据”入口：对象只能由服务端在校验过白名单、哈希、签名或
 * 其他独立事实后创建。客户端报告永远不携带、也不能生成此对象。</p>
 *
 * <p>{@code score} 是服务端根据验证结果给出的分数，不采用客户端自报的 severity。
 * 自动踢人/封禁只使用该分数，避免篡改客户端把一条普通事实包装成高危证据。</p>
 */
public final class ServerEvidence {

    private final int independentSources;
    private final int score;
    private final boolean confirmed;
    private final String verificationCode;

    private ServerEvidence(int independentSources, int score, boolean confirmed, String verificationCode) {
        if (independentSources < 0) {
            throw new IllegalArgumentException("independentSources must not be negative");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("server evidence score out of range");
        }
        if (verificationCode == null || verificationCode.isEmpty() || verificationCode.length() > 64) {
            throw new IllegalArgumentException("verification code is required and bounded");
        }
        this.independentSources = independentSources;
        this.score = score;
        this.confirmed = confirmed;
        this.verificationCode = verificationCode;
    }

    /** 没有经过任何服务端验证：客户端报告最多只能形成观测级证据。 */
    public static ServerEvidence none() {
        return new ServerEvidence(0, 0, false, "NONE");
    }

    /**
     * 服务端完成确证级校验后调用。score 必须来自服务端验证逻辑。
     */
    public static ServerEvidence confirmedByServer(int score, String verificationCode) {
        return new ServerEvidence(2, score, true, verificationCode);
    }

    /**
     * 服务端独立来源互相印证后调用，不代表已经达到确证级。
     */
    public static ServerEvidence corroboratedByServer(int sources, int score, String verificationCode) {
        if (sources < 2) {
            throw new IllegalArgumentException("corroboration requires at least two sources");
        }
        return new ServerEvidence(sources, score, false, verificationCode);
    }

    public int independentSources() {
        return independentSources;
    }

    public int score() {
        return score;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String verificationCode() {
        return verificationCode;
    }
}
