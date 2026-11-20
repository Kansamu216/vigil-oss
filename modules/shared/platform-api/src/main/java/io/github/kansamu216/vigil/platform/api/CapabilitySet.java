package io.github.kansamu216.vigil.platform.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class CapabilitySet {

    private final Set<Capability> capabilities;

    private CapabilitySet(Set<Capability> capabilities) {
        this.capabilities = Collections.unmodifiableSet(capabilities);
    }

    public static CapabilitySet of(Capability... capabilities) {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        for (Capability capability : capabilities) {
            set.add(capability);
        }
        return new CapabilitySet(set);
    }

    public static CapabilitySet none() {
        return new CapabilitySet(EnumSet.noneOf(Capability.class));
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    public Set<Capability> all() {
        return capabilities;
    }

    public Set<Capability> missingFrom(Capability... expected) {
        EnumSet<Capability> missing = EnumSet.noneOf(Capability.class);
        for (Capability capability : expected) {
            if (!capabilities.contains(capability)) {
                missing.add(capability);
            }
        }
        return Collections.unmodifiableSet(missing);
    }
}
