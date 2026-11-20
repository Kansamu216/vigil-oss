package io.github.kansamu216.vigil.server.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kansamu216.vigil.model.ModEntry;
import io.github.kansamu216.vigil.model.Observation;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModPolicyTest {

    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String OTHER_SHA =
            "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    @Test
    void missingFileDisablesPolicy() throws Exception {
        ModPolicy policy = ModPolicy.loadIfExists(
                Files.createTempDirectory("vigil-policy").resolve("mods.txt"));
        assertFalse(policy.enabled());
        assertTrue(policy.evaluate(Collections.<ModEntry>emptyList()).clean());
    }

    @Test
    void emptyPolicyDisablesPolicy() {
        ModPolicy policy = ModPolicy.parse(Arrays.asList("", "# comment only"));
        assertFalse(policy.enabled());
    }

    @Test
    void allowedModsPass() {
        ModPolicy policy = ModPolicy.parse(Arrays.asList(
                "minecraft 1.21.1 " + SHA,
                "neoforge *"));
        ModPolicy.Result result = policy.evaluate(Arrays.asList(
                new ModEntry("minecraft", "1.21.1", "client.jar", 1L, SHA),
                new ModEntry("neoforge", "21.1.267", "neoforge.jar", 1L, OTHER_SHA)));
        assertTrue(result.clean());
        assertEquals("ok", result.summary());
    }

    @Test
    void unknownAndPinnedMismatchProduceObservations() {
        ModPolicy policy = ModPolicy.parse(Arrays.asList(
                "minecraft 1.21.1 " + SHA,
                "pinned 1.0 " + SHA));
        ModPolicy.Result result = policy.evaluate(Arrays.asList(
                new ModEntry("minecraft", "1.20.1", "client.jar", 1L, OTHER_SHA),
                new ModEntry("pinned", "1.0", "pinned.jar", 1L, OTHER_SHA),
                new ModEntry("unknown", "1.0", "unknown.jar", 1L, OTHER_SHA)));
        List<Observation> observations = result.observations();
        assertEquals(3, observations.size());
        assertEquals("MOD_POLICY_UNKNOWN", observations.get(0).code());
        assertEquals("MOD_POLICY_VERSION", observations.get(1).code());
        assertEquals("MOD_POLICY_HASH", observations.get(2).code());
        assertTrue(result.summary().contains("unknown=1"));
    }

    @Test
    void emptyModListIsObservable() {
        ModPolicy policy = ModPolicy.parse(Collections.singletonList("minecraft *"));
        ModPolicy.Result result = policy.evaluate(Collections.<ModEntry>emptyList());
        assertFalse(result.clean());
        assertEquals("MOD_POLICY_EMPTY", result.observations().get(0).code());
    }

    @Test
    void duplicateRuleIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ModPolicy.parse(Arrays.asList("foo *", "foo 1.0")));
    }

    @Test
    void malformedHashIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ModPolicy.parse(Collections.singletonList("foo 1.0 not-a-hash")));
    }

    @Test
    void inlineCommentsAreAccepted() {
        ModPolicy policy = ModPolicy.parse(Collections.singletonList("foo 1.0 " + SHA + " # pinned"));
        assertTrue(policy.enabled());
        assertEquals(1, policy.ruleCount());
    }
}
