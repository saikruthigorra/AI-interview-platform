package com.interviewiq.security;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Password policy enforced server-side at registration and password change:
 *   - 10–72 characters (72 is BCrypt's effective input limit)
 *   - at least one lower-case, upper-case, digit and symbol
 *   - no leading/trailing whitespace, no huge character runs
 *   - rejected against a small list of embarrassingly common passwords
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;

    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern RUN = Pattern.compile("(.)\\1{4,}");

    private static final List<String> COMMON = List.of(
            "password", "password1", "passw0rd", "1234567890", "qwerty123", "letmein123",
            "admin1234", "welcome123", "iloveyou1", "changeme1", "interview1");

    /** @return null when the password is acceptable, otherwise a human-readable reason. */
    public static String validate(String password) {
        if (password == null || password.isBlank()) {
            return "Password is required";
        }
        if (password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.length() > MAX_LENGTH) {
            return "Password must be at most " + MAX_LENGTH + " characters";
        }
        if (!password.equals(password.trim())) {
            return "Password must not start or end with whitespace";
        }
        if (!LOWER.matcher(password).find()) {
            return "Password needs at least one lower-case letter";
        }
        if (!UPPER.matcher(password).find()) {
            return "Password needs at least one upper-case letter";
        }
        if (!DIGIT.matcher(password).find()) {
            return "Password needs at least one digit";
        }
        if (!SYMBOL.matcher(password).find()) {
            return "Password needs at least one symbol";
        }
        if (RUN.matcher(password).find()) {
            return "Password must not repeat the same character 5+ times";
        }
        String lower = password.toLowerCase();
        for (String common : COMMON) {
            if (lower.contains(common)) {
                return "That password is too common — pick something unique";
            }
        }
        return null;
    }
}
