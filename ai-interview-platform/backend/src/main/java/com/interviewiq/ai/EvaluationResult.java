package com.interviewiq.ai;

import java.util.List;

/** Normalised evaluation output, whatever engine produced it. */
public record EvaluationResult(
        int score,
        int technicalScore,
        int depthScore,
        int communicationScore,
        int clarityScore,
        String feedback,
        List<String> strengths,
        List<String> improvements,
        List<String> keywordsMatched,
        List<String> missingPoints
) {
}
