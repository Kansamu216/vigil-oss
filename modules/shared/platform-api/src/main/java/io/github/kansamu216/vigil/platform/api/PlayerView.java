package io.github.kansamu216.vigil.platform.api;

import java.util.UUID;

public interface PlayerView {

    UUID uuid();

    String name();

    String address();

    boolean online();
}
