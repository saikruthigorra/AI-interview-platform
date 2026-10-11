package com.interviewiq.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewiq.config.AppProperties;
import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.Level;
import com.interviewiq.domain.Question;
import com.interviewiq.domain.QuestionSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Generates fresh interview questions with an LLM when a key is configured. */
@Component
public class LlmQuestionGenerator implements QuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmQuestionGenerator.class);

    private final LlmClient client;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public LlmQuestionGenerator(LlmClient client, ObjectMapper mapper, AppProperties props) {
        this.client = client;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public String engineName() {
        return props.getAi().getModel();
    }

    @Override
    public List<Question> generate(String role, Difficulty difficulty, int count, Set<Long> excludeQuestionIds,
                                   List<String> skills, Level level) {
        String skillList = (skills == null || skills.isEmpty())
                ? "not specified — cover the role broadly"
                : String.join(", ", skills);
        String calibrated = switch (level == null ? Level.STUDENT : level) {
            case STUDENT -> "a student or early-career candidate (focus on fundamentals, project work and learning ability)";
            case GRADUATE -> "a recent graduate entering their first role (fundamentals plus applied, practical reasoning)";
            case PROFESSIONAL -> "an experienced professional (expect production experience, trade-offs and scale)";
        };

        String system = """
                You are a senior technical interviewer. You write fair, specific interview questions
                that reveal how a candidate thinks. Reply with JSON only.
                """;
        String user = """
                Produce %d interview questions for a "%s" role at %s difficulty.

                The candidate is %s.
                Their skills: %s

                Rules:
                - At least 70%% of the questions must exercise the candidate's listed skills directly.
                - Mix conceptual, applied and trade-off questions.
                - No yes/no questions. Reference realistic scenarios for this level.
                - "expectedKeywords": 4-7 single concepts a strong answer should mention.
                - "idealAnswer": 2-3 sentences describing what a great answer covers.
                - "skills": the 1-3 skill tags (from the candidate's list) this question exercises.

                Return JSON exactly in this shape:
                {"questions":[{"text":"...","topic":"...","difficulty":"%s","skills":["..."],"expectedKeywords":["..."],"idealAnswer":"..."}]}
                """.formatted(count, role, difficulty.name(), calibrated, skillList, difficulty.name());

        List<Question> generated = new ArrayList<>();
        try {
            JsonNode root;
            try {
                root = mapper.readTree(LlmClient.extractJson(client.chat(system, user)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Could not parse the model's questions JSON: " + ex.getMessage(), ex);
            }
            for (JsonNode node : root.path("questions")) {
                String text = node.path("text").asText("").trim();
                if (text.isEmpty()) {
                    continue;
                }
                List<String> keywords = new ArrayList<>();
                for (JsonNode kw : node.path("expectedKeywords")) {
                    String value = kw.asText("").trim();
                    if (!value.isEmpty()) {
                        keywords.add(value);
                    }
                }
                List<String> questionSkills = new ArrayList<>();
                for (JsonNode tag : node.path("skills")) {
                    String value = tag.asText("").trim();
                    if (!value.isEmpty()) {
                        questionSkills.add(value);
                    }
                }
                generated.add(new Question(
                        text,
                        role,
                        node.path("topic").asText("General"),
                        Difficulty.from(node.path("difficulty").asText(difficulty.name())),
                        String.join(",", keywords),
                        node.path("idealAnswer").asText(""),
                        QuestionSource.AI,
                        questionSkills.isEmpty() ? null : String.join(",", questionSkills)));
            }
        } catch (Exception ex) {
            log.warn("LLM question generation failed ({}); falling back to the rule-based generator", ex.getMessage());
            return List.of();
        }
        log.info("LLM generated {} question(s) for {} / {}", generated.size(), role, difficulty);
        return generated;
    }
}
