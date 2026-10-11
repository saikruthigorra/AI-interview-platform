package com.interviewiq.web.dto;

import com.interviewiq.domain.Candidate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record CandidateResponse(
        Long id,
        String name,
        String email,
        String headline,
        String targetRole,
        List<String> skills,
        String education,
        String level,
        Instant createdAt,
        long sessionCount
) {
    public static CandidateResponse from(Candidate c, long sessionCount) {
        return new CandidateResponse(
                c.getId(), c.getName(), c.getEmail(), c.getHeadline(), c.getTargetRole(),
                splitSkills(c.getSkills()), c.getEducation(),
                c.getLevel() == null ? null : c.getLevel().name(),
                c.getCreatedAt(), sessionCount);
    }

    static List<String> splitSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
