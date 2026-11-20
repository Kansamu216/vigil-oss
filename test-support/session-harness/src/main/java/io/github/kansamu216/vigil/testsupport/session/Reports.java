package io.github.kansamu216.vigil.testsupport.session;

import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.ModEntry;
import io.github.kansamu216.vigil.model.Observation;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试样本构造器，不进入交付包。
 */
public final class Reports {

    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private Reports() {
    }

    public static ClientReport clean(String nonce, String playerId) {
        List<ModEntry> mods = new ArrayList<ModEntry>();
        mods.add(new ModEntry("minecraft", "1.21.1", "client.jar", 1024L, SHA));
        return new ClientReport(
                1, nonce, playerId, "tester", mods,
                new ArrayList<Observation>(), SHA, 1_700_000_000_000L);
    }

    public static ClientReport withObservations(
            String nonce, String playerId, Observation... observations) {
        List<Observation> list = new ArrayList<Observation>();
        for (Observation observation : observations) {
            list.add(observation);
        }
        return new ClientReport(
                1, nonce, playerId, "tester", new ArrayList<ModEntry>(), list, SHA, 1_700_000_000_000L);
    }
}
