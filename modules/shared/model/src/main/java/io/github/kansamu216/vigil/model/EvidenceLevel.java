package io.github.kansamu216.vigil.model;

public enum EvidenceLevel {

    NONE(0),

    OBSERVATION(1),

    CORROBORATED(2),

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
