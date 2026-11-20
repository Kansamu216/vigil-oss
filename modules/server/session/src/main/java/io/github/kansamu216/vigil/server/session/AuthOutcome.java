package io.github.kansamu216.vigil.server.session;

public final class AuthOutcome {

    private final AuthResult result;
    private final AuthFailure failure;

    private AuthOutcome(AuthResult result, AuthFailure failure) {
        this.result = result;
        this.failure = failure;
    }

    public static AuthOutcome success(AuthResult result) {
        return new AuthOutcome(result, null);
    }

    public static AuthOutcome failed(AuthFailure failure) {
        return new AuthOutcome(null, failure);
    }

    public boolean isSuccess() {
        return result != null;
    }

    public AuthResult result() {
        if (result == null) {
            throw new IllegalStateException("not a successful outcome: " + failure);
        }
        return result;
    }

    public AuthFailure failure() {
        if (failure == null) {
            throw new IllegalStateException("not a failed outcome");
        }
        return failure;
    }
}
