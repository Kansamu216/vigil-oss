package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.model.Challenge;

/**
 * 待下发的挑战及其签名。
 *
 * <p>签名覆盖挑战的全部字段。</p>
 */
public final class SignedChallenge {

    private final Challenge challenge;
    private final byte[] signature;

    SignedChallenge(Challenge challenge, byte[] signature) {
        this.challenge = challenge;
        this.signature = signature.clone();
    }

    public Challenge challenge() {
        return challenge;
    }

    public byte[] signature() {
        return signature.clone();
    }
}
