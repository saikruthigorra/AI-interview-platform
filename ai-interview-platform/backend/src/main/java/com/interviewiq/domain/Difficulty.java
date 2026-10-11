package com.interviewiq.domain;

/** How hard the generated / bank questions should be. */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD;

    public static Difficulty from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MEDIUM;
        }
    }
}
