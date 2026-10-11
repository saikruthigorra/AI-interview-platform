package com.interviewiq.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewiq.config.AppProperties;
import com.interviewiq.domain.Question;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** LLM-backed answer evaluator with a strict JSON contract. */
@Component
public class LlmAnswerEvaluator implements AnswerEvaluator {

    private final LlmClient client;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public LlmAnswerEvaluator(LlmClient client, ObjectMapper mapper, AppProperties props) {
        this.client = client;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public String engineName() {
        return props.getAi().getModel();
    }

    @Override
    public EvaluationResult evaluate(Question question, String answerText) {
        String system = """
                You are a rigorous but encouraging senior interviewer grading a written interview answer.
                Reply with JSON only.
                """;
        String user = """
                Role: %s
                Difficulty: %s
                Question: %s
                Concepts a strong answer should cover: %s
                Reference answer (not the only correct one): %s

                Candidate answer:
                \"\"\"
                %s
                \"\"\"

                Grade it honestly. Scores are integers 0-100.
                Return JSON exactly in this shape:
                {"score":0,"technicalScore":0,"depthScore":0,"communicationScore":0,"clarityScore":0,
                 "feedback":"2-4 sentences of specific, actionable coaching",
                 "strengths":["..."],"improvements":["..."],
                 "keywordsMatched":["..."],"missingPoints":["..."]}
                """.formatted(
                question.getRole(),
                question.getDifficulty().name(),
                question.getText(),
                question.getExpectedKeywords() == null ? "(not specified)" : question.getExpectedKeywords(),
                question.getIdealAnswer() == null ? "(not specified)" : question.getIdealAnswer(),
                answerText == null || answerText.isBlank() ? "(blank)" : answerText);

        JsonNode root;
        try {
            root = mapper.readTree(LlmClient.extractJson(client.chat(system, user)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse the model's evaluation JSON: " + ex.getMessage(), ex);
        }
        return new EvaluationResult(
                clamp(root.path("score").asInt(50)),
                clamp(root.path("technicalScore").asInt(50)),
                clamp(root.path("depthScore").asInt(50)),
                clamp(root.path("communicationScore").asInt(50)),
                clamp(root.path("clarityScore").asInt(50)),
                root.path("feedback").asText("Evaluated by " + props.getAi().getModel() + "."),
                strings(root.path("strengths")),
                strings(root.path("improvements")),
                strings(root.path("keywordsMatched")),
                strings(root.path("missingPoints")));
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    out.add(value);
                }
            });
        }
        return out;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
