package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.model.Challenge;

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
