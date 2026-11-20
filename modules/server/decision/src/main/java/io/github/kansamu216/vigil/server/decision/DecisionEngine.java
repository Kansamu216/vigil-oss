package io.github.kansamu216.vigil.server.decision;

import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.EvidenceLevel;
import io.github.kansamu216.vigil.model.Observation;
import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.model.VerdictAction;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DecisionEngine {

    private static final String STATS_PREFIX = "STATS_";
    private static final String COLLECT_PREFIX = "COLLECT_";

    private final DecisionPolicy policy;

    public DecisionEngine(DecisionPolicy policy) {
        this.policy = policy;
    }

    public Verdict evaluate(ClientReport report) {
        return evaluate(report, ServerEvidence.none());
    }

    public Verdict evaluate(ClientReport report, ServerEvidence verification) {
        if (verification == null) {
            verification = ServerEvidence.none();
        }
        int clientScore = 0;
        int usable = 0;
        boolean onlyStatistics = true;
        Set<String> codes = new LinkedHashSet<String>();

        for (Observation observation : report.observations()) {
            String code = observation.code();
            if (code.startsWith(COLLECT_PREFIX)) {
                continue;
            }
            usable++;
            clientScore = Math.min(100, clientScore + observation.severity());
            codes.add(code);
            if (!code.startsWith(STATS_PREFIX)) {
                onlyStatistics = false;
            }
        }

        if (usable == 0 && !verification.isConfirmed() && verification.independentSources() == 0) {
            return new Verdict(EvidenceLevel.NONE, 0, "no usable evidence", VerdictAction.NONE);
        }

        EvidenceLevel level;
        if (verification.isConfirmed()) {
            level = EvidenceLevel.CONFIRMED;
        } else if (verification.independentSources() >= 2) {
            level = EvidenceLevel.CORROBORATED;
        } else if (usable > 0) {
            level = EvidenceLevel.OBSERVATION;
        } else {
            level = EvidenceLevel.NONE;
        }

        int score = Math.min(100, Math.max(clientScore, verification.score()));
        VerdictAction action = decideAction(level, clientScore, verification, onlyStatistics);
        return new Verdict(
                level,
                score,
                summarize(codes, onlyStatistics, verification),
                action);
    }

    private VerdictAction decideAction(
            EvidenceLevel level, int clientScore, ServerEvidence verification, boolean onlyStatistics) {
        if (onlyStatistics && verification.independentSources() == 0) {
            return clientScore >= policy.alertScore() ? VerdictAction.ALERT : VerdictAction.NONE;
        }
        int serverScore = verification.score();
        if (policy.autoBanEnabled()
                && level.atLeast(EvidenceLevel.CONFIRMED)
                && serverScore >= policy.banScore()) {
            return VerdictAction.BAN;
        }
        if (policy.autoKickEnabled()
                && level.atLeast(EvidenceLevel.CORROBORATED)
                && serverScore >= policy.restrictScore()) {
            return VerdictAction.RESTRICT;
        }
        if (policy.autoKickEnabled()
                && level.atLeast(EvidenceLevel.CORROBORATED)
                && serverScore >= policy.kickScore()) {
            return VerdictAction.KICK;
        }
        if (Math.max(clientScore, serverScore) >= policy.alertScore()) {
            return VerdictAction.ALERT;
        }
        return VerdictAction.NONE;
    }

    private String summarize(Set<String> codes, boolean onlyStatistics, ServerEvidence verification) {
        StringBuilder builder = new StringBuilder();
        if (onlyStatistics) {
            builder.append("statistics only: ");
        }
        if (verification.independentSources() > 0) {
            builder.append("server=").append(verification.verificationCode()).append("; ");
        }
        int index = 0;
        for (String code : codes) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(code);
            if (++index >= 4) {
                builder.append(",…");
                break;
            }
        }
        return builder.toString();
    }
}
