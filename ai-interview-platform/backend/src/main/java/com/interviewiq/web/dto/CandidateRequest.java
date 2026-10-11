package com.interviewiq.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 190) String email,
        @Size(max = 190) String headline,
        @Size(max = 120) String targetRole,
        /** Comma-separated skills, e.g. "React,TypeScript,CSS". */
        @Size(max = 500) String skills,
        @Size(max = 190) String education,
        /** STUDENT | GRADUATE | PROFESSIONAL */
        String level
) {
}
