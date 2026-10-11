package com.interviewiq.ai;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Lightweight, dependency-free sentiment analysis for interview reviews.
 *
 * The rating carries most of the signal (it is explicit), so the comment text
 * nudges the score rather than deciding it. Negation ("not helpful") flips the
 * polarity of the word it precedes, which handles the common review phrasings.
 */
@Component
public class SentimentAnalyzer {

    public record Result(String label, int score, List<String> positiveTerms, List<String> negativeTerms) {
    }

    private static final List<String> POSITIVE = List.of(
            "helpful", "useful", "great", "excellent", "insightful", "valuable", "clear", "detailed",
            "practical", "relevant", "accurate", "fair", "strong", "improved", "growth", "recommend",
            "easy", "smooth", "fast", "smart", "precise", "encouraging", "spot on", "loved", "like it",
            "good", "solid", "well done", "thorough", "actionable", "realistic");

    private static final List<String> NEGATIVE = List.of(
            "unhelpful", "useless", "poor", "bad", "confusing", "vague", "generic", "shallow",
            "unfair", "inaccurate", "misleading", "wrong", "biased", "irrelevant", "slow", "buggy",
            "frustrating", "disappointing", "difficult", "unclear", "pointless", "waste", "broken",
            "not helpful", "too hard", "too easy", "repetitive", "hate");

    private static final List<String> NEGATORS = List.of("not", "isn't", "wasn't", "no", "never", "hardly", "barely");

    public Result analyze(Integer rating, String comment) {
        String text = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
        List<String> positives = POSITIVE.stream().filter(text::contains).toList();
        List<String> negatives = NEGATIVE.stream().filter(text::contains).toList();

        // Rating prior: 3 stars = neutral baseline, each star ±18 points.
        int score = 50;
        if (rating != null) {
            score = 50 + (clamp(rating, 1, 5) - 3) * 18;
        }
        score += 6 * positives.size() - 8 * negatives.size();

        // "not helpful" style: a negator shortly before a positive term inverts it.
        for (String positive : positives) {
            int index = text.indexOf(positive);
            String window = text.substring(Math.max(0, index - 12), index);
            if (NEGATORS.stream().anyMatch(window::contains)) {
                score -= 14;
            }
        }

        score = clamp(score, 0, 100);
        String label = score >= 65 ? "POSITIVE" : score >= 40 ? "NEUTRAL" : "NEGATIVE";
        return new Result(label, score, positives, negatives);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
