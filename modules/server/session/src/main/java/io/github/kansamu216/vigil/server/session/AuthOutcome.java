package io.github.kansamu216.vigil.server.session;

/**
 * 握手结果：成功带 {@link AuthResult}，失败带 {@link AuthFailure}。
 */
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

    /** 运行时与平台适配层需要构造协议层失败结果。 */
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
