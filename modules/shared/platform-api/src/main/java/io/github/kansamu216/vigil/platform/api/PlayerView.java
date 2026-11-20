package io.github.kansamu216.vigil.platform.api;

import java.util.UUID;

/**
 * 玩家只读视图：核心不得接触各平台的 Player 类型。
 */
public interface PlayerView {

    UUID uuid();

    String name();

    String address();

    boolean online();
}
