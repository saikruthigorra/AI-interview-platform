package com.interviewiq.domain;

/** Where a candidate sits in their career — shapes how questions are pitched. */
public enum Level {
    STUDENT,
    GRADUATE,
    PROFESSIONAL;

    public static Level from(String raw) {
        if (raw == null || raw.isBlank()) {
            return STUDENT;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return STUDENT;
        }
    }
}
