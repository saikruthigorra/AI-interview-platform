package com.interviewiq.ai;

import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.Question;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Deterministic, dependency-free answer evaluator.
 *
 * It scores along the same four axes the LLM is asked for — technical coverage,
 * depth, communication and clarity — using concept coverage against the
 * question's expected keywords plus linguistic signals (structure, examples,
 * quantification, filler, run-on sentences). It is fast, explainable and always
 * available, which makes it a good default for demos, tests and as the fallback
 * whenever a remote model errors out.
 */
@Component
public class HeuristicAnswerEvaluator implements AnswerEvaluator {

    private static final String[] STRUCTURE_MARKERS = {
            "first", "second", "third", "then", "next", "finally", "because", "so that",
            "however", "for example", "for instance", "in practice", "additionally",
            "on the other hand", "trade-off", "tradeoff", "the reason"};

    private static final String[] EXAMPLE_MARKERS = {
            "for example", "for instance", "e.g", "in my project", "i built", "i worked",
            "we used", "we implemented", "at my previous", "in production", "recently"};

    private static final String[] TRADEOFF_MARKERS = {
            "trade-off", "tradeoff", "however", "downside", "versus", " vs ", "at the cost",
            "but it", "the catch", "caveat"};

    private static final String[] FILLER_MARKERS = {
            "basically", "actually", "stuff", "things like that", "whatever", "kind of",
            "sort of", "you know"};

    @Override
    public String engineName() {
        return "heuristic";
    }

    @Override
    public EvaluationResult evaluate(Question question, String answerText) {
        String answer = answerText == null ? "" : answerText.trim();
        String lower = answer.toLowerCase(Locale.ROOT);
        int words = wordCount(answer);

        if (words < 5) {
            return new EvaluationResult(
                    12, 5, 5, 25, 25,
                    "No substantive answer was recorded. In a real interview, think out loud: state your approach, "
                            + "walk through it step by step, then wrap up with trade-offs.",
                    List.of("Attempted the question"),
                    List.of("Give a full answer: approach → reasoning → example → trade-offs",
                            "Aim for 90–150 words per answer"),
                    List.of(),
                    splitKeywords(question.getExpectedKeywords()));
        }

        List<String> expected = splitKeywords(question.getExpectedKeywords());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String keyword : expected) {
            if (mentions(lower, keyword)) {
                matched.add(keyword);
            } else {
                missing.add(keyword);
            }
        }

        double coverage = expected.isEmpty() ? 0.55 : (double) matched.size() / expected.size();
        double lengthFactor = Math.min(1.0, words / 110.0);
        int structureSignals = countDistinct(lower, STRUCTURE_MARKERS);
        boolean hasExample = containsAny(lower, EXAMPLE_MARKERS);
        boolean hasNumbers = answer.matches(".*\\d.*");
        boolean hasTradeoff = containsAny(lower, TRADEOFF_MARKERS);
        int fillerCount = countOccurrences(lower, FILLER_MARKERS);
        int sentences = Math.max(1, answer.split("[.!?]+").length);
        double avgSentenceLength = (double) words / sentences;
        boolean multiParagraph = answer.split("\n\\s*\n").length > 1;

        double base = 8 + 62 * coverage + 18 * lengthFactor;
        double bonuses = Math.min(6, structureSignals * 1.5)
                + (hasNumbers ? 3 : 0)
                + (hasExample ? 4 : 0)
                + (question.getDifficulty() == Difficulty.HARD && hasTradeoff ? 4 : 0);
        double penalties = Math.min(8, fillerCount * 2.0)
                + (avgSentenceLength > 45 ? 6 : 0)
                + (words > 320 ? 4 : 0);

        int score = clamp((int) Math.round(base + bonuses - penalties), 5, 97);
        int technical = clamp((int) Math.round(10 + 85 * coverage + (hasNumbers ? 3 : 0)), 5, 98);
        int depth = clamp((int) Math.round(12 + 60 * lengthFactor + 24 * coverage), 5, 98);
        int communication = clamp((int) Math.round(
                35 + 8 * Math.min(4, sentences) + Math.min(18, structureSignals * 4.5)
                        + (multiParagraph ? 8 : 0) - penalties), 10, 98);
        int clarity = clamp((int) Math.round(communication * 0.6 + technical * 0.4 - fillerCount * 1.5), 5, 98);

        String feedback = buildFeedback(question, words, coverage, matched, missing,
                structureSignals, hasExample, hasNumbers, hasTradeoff, avgSentenceLength);
        List<String> strengths = buildStrengths(coverage, matched, structureSignals, hasExample, words, hasNumbers);
        List<String> improvements = buildImprovements(words, missing, structureSignals, hasExample,
                hasTradeoff, avgSentenceLength, question.getDifficulty());

        return new EvaluationResult(score, technical, depth, communication, clarity,
                feedback, strengths, improvements, matched, missing);
    }

    // ── Narrative generation ─────────────────────────────────────────────────

    private String buildFeedback(Question question, int words, double coverage, List<String> matched,
                                 List<String> missing, int structureSignals, boolean hasExample,
                                 boolean hasNumbers, boolean hasTradeoff, double avgSentenceLength) {
        List<String> parts = new ArrayList<>();
        String verdict = coverage >= 0.75 && words >= 60 ? "Strong answer"
                : coverage >= 0.5 ? "Solid answer"
                : coverage >= 0.25 ? "Developing answer"
                : "Thin answer";
        parts.add(verdict + " for a " + question.getDifficulty().name().toLowerCase(Locale.ROOT)
                + "-difficulty question: it lands " + matched.size() + " of the "
                + (matched.size() + missing.size()) + " concepts a grader would look for.");

        if (!matched.isEmpty()) {
            parts.add("You hit " + joinSample(matched, 4) + ".");
        }
        if (!missing.isEmpty()) {
            parts.add("Notably absent: " + joinSample(missing, 4) + " — missing those is what separates a pass from a standout.");
        }
        if (words < 25) {
            parts.add("The answer is very brief; add your reasoning and one concrete example to make it persuasive.");
        } else if (words > 320) {
            parts.add("Great depth, but for interview pacing tighten it to roughly 90–150 words around a clear spine.");
        }
        if (structureSignals == 0) {
            parts.add("Signpost your reasoning (\"first… because… for example…\") so a listener can follow in real time.");
        }
        if (!hasExample) {
            parts.add("Ground it in something you actually did — the interviewer is sampling your experience, not a textbook.");
        }
        if (question.getDifficulty() == Difficulty.HARD && !hasTradeoff) {
            parts.add("For hard questions, close with the trade-offs and when you would pick an alternative approach.");
        }
        if (avgSentenceLength > 45) {
            parts.add("Several sentences run long; short sentences read as clear thinking.");
        }
        return String.join(" ", parts);
    }

    private List<String> buildStrengths(double coverage, List<String> matched, int structureSignals,
                                        boolean hasExample, int words, boolean hasNumbers) {
        List<String> out = new ArrayList<>();
        if (coverage >= 0.5 && !matched.isEmpty()) {
            out.add("Covered core concepts: " + joinSample(matched, 5));
        }
        if (structureSignals >= 2) {
            out.add("Well-organised explanation with clear signposting");
        }
        if (hasExample) {
            out.add("Backed up with a concrete example");
        }
        if (hasNumbers) {
            out.add("Quantified impact with specific numbers");
        }
        if (words >= 60) {
            out.add("Substantive depth rather than a superficial answer");
        }
        if (out.isEmpty()) {
            out.add("Engaged with the question and made an attempt to explain");
        }
        return out;
    }

    private List<String> buildImprovements(int words, List<String> missing, int structureSignals,
                                           boolean hasExample, boolean hasTradeoff,
                                           double avgSentenceLength, Difficulty difficulty) {
        List<String> out = new ArrayList<>();
        if (!missing.isEmpty()) {
            out.add("Expand on: " + joinSample(missing, 5));
        }
        if (words < 25) {
            out.add("Structure answers as approach → reasoning → example → trade-offs (2 minutes of speech)");
        }
        if (structureSignals == 0) {
            out.add("Use explicit signposting words to guide the listener through your reasoning");
        }
        if (!hasExample) {
            out.add("Prepare one concrete story per core topic from your own experience");
        }
        if (difficulty == Difficulty.HARD && !hasTradeoff) {
            out.add("Practice stating trade-offs: what you gain, what you give up, when you'd choose differently");
        }
        if (avgSentenceLength > 45) {
            out.add("Break long sentences into shorter ones for clarity");
        }
        if (out.isEmpty()) {
            out.add("Keep practising: rehearse a crisp 90-second version of this answer aloud");
        }
        return out;
    }

    // ── Text utilities ───────────────────────────────────────────────────────

    private static int wordCount(String text) {
        if (text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static List<String> splitKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static boolean mentions(String lowerAnswer, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            return false;
        }
        if (lowerAnswer.contains(needle)) {
            return true;
        }
        // multi-word keywords: accept when every word appears in any order nearby
        String[] words = needle.split("\\s+");
        if (words.length < 2) {
            return false;
        }
        for (String w : words) {
            if (w.length() > 3 && !lowerAnswer.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private static int countDistinct(String lowerAnswer, String[] markers) {
        int count = 0;
        for (String marker : markers) {
            if (lowerAnswer.contains(marker)) {
                count++;
            }
        }
        return count;
    }

    private static int countOccurrences(String lowerAnswer, String[] markers) {
        int count = 0;
        for (String marker : markers) {
            int index = lowerAnswer.indexOf(marker);
            while (index >= 0) {
                count++;
                index = lowerAnswer.indexOf(marker, index + marker.length());
            }
        }
        return count;
    }

    private static boolean containsAny(String lowerAnswer, String[] markers) {
        return Arrays.stream(markers).anyMatch(lowerAnswer::contains);
    }

    private static String joinSample(List<String> items, int max) {
        return String.join(", ", items.subList(0, Math.min(max, items.size())));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
