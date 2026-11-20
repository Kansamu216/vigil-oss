package io.github.kansamu216.vigil.client.agent;

public class ClientHandshakeException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {

        UNTRUSTED_SERVER,

        NO_TRUST_ANCHOR,

        INVALID_KEY,

        SIGNATURE_INVALID,

        EXPIRED,

        NOT_VERIFIED,

        REPORT_MISMATCH
    }

    private final Reason reason;

    public ClientHandshakeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ClientHandshakeException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
