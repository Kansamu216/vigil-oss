package io.github.kansamu216.vigil.server.decision;

public final class DecisionPolicy {

    private final int alertScore;
    private final int kickScore;
    private final int restrictScore;
    private final int banScore;
    private final boolean autoBanEnabled;
    private final boolean autoKickEnabled;

    public DecisionPolicy(
            int alertScore,
            int kickScore,
            int restrictScore,
            int banScore,
            boolean autoBanEnabled,
            boolean autoKickEnabled) {
        if (alertScore < 0 || kickScore < alertScore || restrictScore < kickScore || banScore < restrictScore) {
            throw new IllegalArgumentException("thresholds must be non-decreasing and non-negative");
        }
        if (banScore > 100) {
            throw new IllegalArgumentException("ban score above 100 is unreachable");
        }
        this.alertScore = alertScore;
        this.kickScore = kickScore;
        this.restrictScore = restrictScore;
        this.banScore = banScore;
        this.autoBanEnabled = autoBanEnabled;
        this.autoKickEnabled = autoKickEnabled;
    }

    public static DecisionPolicy defaults() {
        return new DecisionPolicy(20, 50, 70, 90, false, false);
    }

    public int alertScore() {
        return alertScore;
    }

    public int kickScore() {
        return kickScore;
    }

    public int restrictScore() {
        return restrictScore;
    }

    public int banScore() {
        return banScore;
    }

    public boolean autoBanEnabled() {
        return autoBanEnabled;
    }

    public boolean autoKickEnabled() {
        return autoKickEnabled;
    }
}
