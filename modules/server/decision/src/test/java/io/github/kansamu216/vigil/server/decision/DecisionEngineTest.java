package io.github.kansamu216.vigil.server.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.EvidenceLevel;
import io.github.kansamu216.vigil.model.Observation;
import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.model.VerdictAction;
import io.github.kansamu216.vigil.testsupport.session.Reports;
import org.junit.jupiter.api.Test;

/**
 * 判定门槛与最小信任回归。
 *
 * <p>重点防止“客户端自报一个高危消息码，就被服务端当成确证证据”。</p>
 */
class DecisionEngineTest {

    private static final String NONCE = "0123456789abcdef";
    private static final String PLAYER = "player-uuid-1";

    private static ClientReport report(Observation... observations) {
        return Reports.withObservations(NONCE, PLAYER, observations);
    }

    @Test
    void noObservationsMeansClean() {
        Verdict verdict = new DecisionEngine(DecisionPolicy.defaults()).evaluate(report());
        assertEquals(EvidenceLevel.NONE, verdict.level());
        assertEquals(VerdictAction.NONE, verdict.action());
    }

    @Test
    void statisticsOnlyNeverTriggersAction() {
        DecisionPolicy policy = new DecisionPolicy(10, 20, 30, 40, true, true);
        Verdict verdict = new DecisionEngine(policy).evaluate(report(
                new Observation("STATS_CPS_HIGH", "cps=18", 90),
                new Observation("STATS_AIM_SNAP", "snap", 90)));
        assertEquals(VerdictAction.ALERT, verdict.action());
        assertEquals(EvidenceLevel.OBSERVATION, verdict.level());
    }

    @Test
    void collectionFailureIsNotEvidence() {
        Verdict verdict = new DecisionEngine(DecisionPolicy.defaults()).evaluate(report(
                new Observation("COLLECT_MOD_LIST_TIMEOUT", "timeout after 2000ms", 100)));
        assertEquals(EvidenceLevel.NONE, verdict.level());
        assertEquals(VerdictAction.NONE, verdict.action());
        assertEquals(0, verdict.score());
    }

    @Test
    void clientCannotSelfAssertConfirmedIntegrityEvidence() {
        DecisionPolicy permissive = new DecisionPolicy(10, 40, 60, 90, true, true);
        Verdict verdict = new DecisionEngine(permissive).evaluate(report(
                new Observation("INTEGRITY_HASH_MISMATCH", "client supplied claim", 100)));
        assertEquals(EvidenceLevel.OBSERVATION, verdict.level());
        assertEquals(VerdictAction.ALERT, verdict.action());
    }

    @Test
    void clientOnlyObservationsNeverCorroborateOrAutoAct() {
        DecisionPolicy permissive = new DecisionPolicy(10, 40, 60, 90, true, true);
        Verdict verdict = new DecisionEngine(permissive).evaluate(report(
                new Observation("BEHAVIOR_FAST_BREAK", "x", 100),
                new Observation("BEHAVIOR_REACH", "y", 100)));
        assertEquals(EvidenceLevel.OBSERVATION, verdict.level());
        assertEquals(VerdictAction.ALERT, verdict.action());
        assertEquals(100, verdict.score());
    }

    @Test
    void serverConfirmedEvidenceCanBanOnlyWhenEnabled() {
        ClientReport report = report(new Observation("INTEGRITY_HASH_MISMATCH", "mod foo", 20));
        ServerEvidence confirmed = ServerEvidence.confirmedByServer(100, "SERVER_MOD_HASH_MATCH");

        Verdict alertOnly = new DecisionEngine(DecisionPolicy.defaults()).evaluate(report, confirmed);
        assertEquals(EvidenceLevel.CONFIRMED, alertOnly.level());
        assertEquals(VerdictAction.ALERT, alertOnly.action());

        DecisionPolicy enabled = new DecisionPolicy(10, 40, 60, 90, true, true);
        Verdict banned = new DecisionEngine(enabled).evaluate(report, confirmed);
        assertEquals(VerdictAction.BAN, banned.action());
    }

    @Test
    void serverEvidenceMustMeetItsOwnThreshold() {
        DecisionPolicy enabled = new DecisionPolicy(10, 40, 60, 90, true, true);
        Verdict verdict = new DecisionEngine(enabled).evaluate(
                report(new Observation("INTEGRITY_HASH_MISMATCH", "mod foo", 100)),
                ServerEvidence.confirmedByServer(20, "SERVER_MOD_HASH_MATCH"));
        assertEquals(EvidenceLevel.CONFIRMED, verdict.level());
        assertEquals(VerdictAction.ALERT, verdict.action());
    }

    @Test
    void corroborationRequiresIndependentServerSources() {
        DecisionPolicy enabled = new DecisionPolicy(10, 40, 60, 90, true, true);
        Verdict verdict = new DecisionEngine(enabled).evaluate(
                report(new Observation("BEHAVIOR_FAST_BREAK", "x", 10)),
                ServerEvidence.corroboratedByServer(2, 50, "SERVER_TWO_CHECKS"));
        assertEquals(EvidenceLevel.CORROBORATED, verdict.level());
        assertEquals(VerdictAction.KICK, verdict.action());
    }

    @Test
    void scoreIsCappedAtHundred() {
        Verdict verdict = new DecisionEngine(DecisionPolicy.defaults()).evaluate(report(
                new Observation("BEHAVIOR_A", "x", 80),
                new Observation("BEHAVIOR_B", "y", 80)));
        assertEquals(100, verdict.score());
    }
}
