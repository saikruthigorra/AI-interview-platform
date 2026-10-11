package com.interviewiq.ai;

import java.util.List;

/** End-of-interview rollup produced by the AI layer. */
public record SessionSummary(String summary, List<String> strengths, List<String> gaps) {

    public String strengthsText() {
        return strengths == null ? null : String.join("\n", strengths);
    }

    public String gapsText() {
        return gaps == null ? null : String.join("\n", gaps);
    }
}
