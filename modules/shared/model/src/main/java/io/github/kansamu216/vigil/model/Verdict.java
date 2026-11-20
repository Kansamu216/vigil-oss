package io.github.kansamu216.vigil.model;

public final class Verdict {

    private final EvidenceLevel level;
    private final int score;
    private final String reason;
    private final VerdictAction action;

    public Verdict(EvidenceLevel level, int score, String reason, VerdictAction action) {
        if (level == null || action == null) {
            throw new IllegalArgumentException("level and action are required");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score out of range: " + score);
        }
        if (reason == null || reason.length() > 256) {
            throw new IllegalArgumentException("invalid reason");
        }
        if (action == VerdictAction.BAN && !level.atLeast(EvidenceLevel.CONFIRMED)) {
            throw new IllegalArgumentException("ban requires CONFIRMED evidence");
        }
        this.level = level;
        this.score = score;
        this.reason = reason;
        this.action = action;
    }

    public static Verdict clean(int score) {
        return new Verdict(EvidenceLevel.NONE, score, "no evidence", VerdictAction.NONE);
    }

    public EvidenceLevel level() {
        return level;
    }

    public int score() {
        return score;
    }

    public String reason() {
        return reason;
    }

    public VerdictAction action() {
        return action;
    }

    public boolean requiresAction() {
        return action != VerdictAction.NONE;
    }

    @Override
    public String toString() {
        return "Verdict{" + level + ", score=" + score + ", action=" + action + ", " + reason + "}";
    }
}
