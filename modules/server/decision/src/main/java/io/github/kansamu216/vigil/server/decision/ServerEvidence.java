package io.github.kansamu216.vigil.server.decision;

/**
 * 服务端验证结果，携带服务端评分。
 *
 * <p>该对象由服务端验证流程创建，不接收客户端自报的分数。</p>
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

    /** 未进行服务端验证的观测事实。 */
    public static ServerEvidence none() {
        return new ServerEvidence(0, 0, false, "NONE");
    }

    /**
     * 服务端完成校验后调用，分数来自服务端验证逻辑。
     */
    public static ServerEvidence confirmedByServer(int score, String verificationCode) {
        return new ServerEvidence(2, score, true, verificationCode);
    }

    /**
     * 服务端多个来源印证后调用。
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
