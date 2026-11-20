package io.github.kansamu216.vigil.model;

public final class Observation {

    private final String code;
    private final String detail;
    private final int severity;

    public Observation(String code, String detail, int severity) {
        if (code == null || code.isEmpty() || code.length() > 64) {
            throw new IllegalArgumentException("invalid observation code");
        }
        if (detail == null || detail.length() > 256) {
            throw new IllegalArgumentException("invalid observation detail");
        }
        if (severity < 0 || severity > 100) {
            throw new IllegalArgumentException("severity out of range: " + severity);
        }
        this.code = code;
        this.detail = detail;
        this.severity = severity;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    public int severity() {
        return severity;
    }

    @Override
    public String toString() {
        return code + "(severity=" + severity + ")";
    }
}
