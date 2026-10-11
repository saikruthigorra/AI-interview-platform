package com.interviewiq.domain;

/** Lifecycle of a recruiter-hosted assessment. */
public enum AssessmentStatus {
    /** Being built: questions can be generated/edited freely, no attempts possible. */
    DRAFT,
    /** Shareable via its access code; candidates can take it. */
    PUBLISHED,
    /** No new attempts; existing results stay. */
    CLOSED
}
