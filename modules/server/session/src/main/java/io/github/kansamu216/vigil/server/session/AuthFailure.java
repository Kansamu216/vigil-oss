package io.github.kansamu216.vigil.server.session;

public enum AuthFailure {

    UNKNOWN_SESSION,

    NONCE_MISMATCH,

    CHALLENGE_EXPIRED,

    KEY_UNWRAP_FAILED,

    REPORT_DECRYPT_FAILED,

    REPORT_MALFORMED,

    RATE_LIMITED,

    ALREADY_AUTHENTICATED,

    INTERNAL_ERROR
}
