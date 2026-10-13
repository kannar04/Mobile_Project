package com.gmail.manhle240406.project.auth;

/** Registration policy never blocks an existing valid short-password sign-in. */
public final class AuthInput {
    public enum Error { NONE, EMAIL, PASSWORD_REQUIRED, PASSWORD_POLICY, CONFIRMATION }
    private AuthInput() {}
    public static Error validate(String email, String password, String confirmation, boolean registering) {
        if (email == null || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) return Error.EMAIL;
        if (password == null || password.isEmpty()) return Error.PASSWORD_REQUIRED;
        int length = password.codePointCount(0, password.length());
        if (registering && (length < 12 || length > 128)) return Error.PASSWORD_POLICY;
        if (registering && !password.equals(confirmation)) return Error.CONFIRMATION;
        return Error.NONE;
    }
}
