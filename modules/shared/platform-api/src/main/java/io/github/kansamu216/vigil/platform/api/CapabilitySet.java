package io.github.kansamu216.vigil.platform.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 平台能力集合。
 *
 * <p>业务代码用 {@link #supports(Capability)} 分支，而不是试探性调用再吞异常。</p>
 */
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

    /** 缺失能力清单，用于启动时输出一次性的降级说明。 */
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
