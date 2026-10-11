package com.interviewiq.ai;

import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.Level;
import com.interviewiq.domain.Question;
import com.interviewiq.domain.QuestionSource;
import com.interviewiq.repo.QuestionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Offline question source. Two tricks make it feel tailored without an LLM:
 *
 *   1. questions are *ranked by skill overlap* against the student's declared
 *      skills (tags like "React,JavaScript"), so a React-heavy student gets
 *      React-heavy questions rather than a random slice of the role's bank;
 *   2. when the bank runs short it generates procedural variants that name the
 *      student's actual skills, pitched by their level (student/graduate/pro).
 */
@Component
public class HeuristicQuestionGenerator implements QuestionGenerator {

    private final QuestionRepository questions;

    public HeuristicQuestionGenerator(QuestionRepository questions) {
        this.questions = questions;
    }

    @Override
    public String engineName() {
        return "bank";
    }

    @Override
    public List<Question> generate(String role, Difficulty difficulty, int count,
                                   Set<Long> excludeQuestionIds, List<String> skills, Level level) {
        Set<Long> exclude = excludeQuestionIds == null ? Set.of() : excludeQuestionIds;
        List<String> wanted = normalizeSkills(skills);

        List<Question> pool = new ArrayList<>(questions.findByRole(role));
        if (pool.isEmpty()) {
            pool.addAll(questions.findAll());   // last resort: any role beats an empty session
        }

        // Deterministic ranking: skill overlap first, then difficulty fit, then id.
        pool.sort(Comparator
                .comparingInt((Question q) -> overlap(q, wanted)).reversed()
                .thenComparingInt(q -> q.getDifficulty() == difficulty ? 0 : 1)
                .thenComparing(q -> q.getId() == null ? Long.MAX_VALUE : q.getId()));

        List<Question> picked = new ArrayList<>();
        Set<Long> taken = new HashSet<>(exclude);
        for (Question q : pool) {
            if (picked.size() >= count) {
                break;
            }
            if (q.getId() != null && !taken.contains(q.getId())) {
                taken.add(q.getId());
                picked.add(q);
            }
        }

        // Fresh questions exhausted? Reuse the strongest matches — a repeat of the
        // same role/difficulty beats serving nothing.
        for (Question q : pool) {
            if (picked.size() >= count) {
                break;
            }
            if (q.getId() != null && !taken.contains(q.getId())) {
                taken.add(q.getId());
                picked.add(q);
            }
        }

        // Still short? Procedural variants built from the student's own skills.
        int variant = 1;
        while (picked.size() < count && variant <= count * 6) {
            picked.add(variantFor(role, difficulty, wanted, level, variant++));
        }
        return picked.size() > count ? picked.subList(0, count) : picked;
    }

    // ── Skill matching ───────────────────────────────────────────────────────

    static List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /** Number of the student's skills this question's tags (or text) cover. */
    static int overlap(Question question, List<String> wantedSkills) {
        if (wantedSkills.isEmpty()) {
            return 0;
        }
        String haystack = ((question.getSkillTags() == null ? "" : question.getSkillTags()) + ","
                + (question.getTopic() == null ? "" : question.getTopic()) + ","
                + question.getText().toLowerCase(Locale.ROOT)).toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String skill : wantedSkills) {
            if (haystack.contains(skill) || compatible(haystack, skill)) {
                hits++;
            }
        }
        return hits;
    }

    /** Handles obvious aliases so "js" matches "JavaScript", "k8s" matches "Kubernetes", etc. */
    private static boolean compatible(String haystack, String skill) {
        for (Map.Entry<String, String[]> alias : ALIASES.entrySet()) {
            for (String spelling : alias.getValue()) {
                if (skill.equals(spelling) && haystack.contains(alias.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Map<String, String[]> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("javascript", new String[]{"js", "es6", "ecmascript"});
        ALIASES.put("typescript", new String[]{"ts"});
        ALIASES.put("react", new String[]{"reactjs", "react.js"});
        ALIASES.put("kubernetes", new String[]{"k8s"});
        ALIASES.put("docker", new String[]{"containerization"});
        ALIASES.put("sql", new String[]{"mysql", "postgres", "postgresql", "tidb"});
        ALIASES.put("java", new String[]{"jdk", "jvm"});
        ALIASES.put("spring boot", new String[]{"spring", "springboot"});
        ALIASES.put("python", new String[]{"py"});
        ALIASES.put("machine learning", new String[]{"ml", "deep learning"});
        ALIASES.put("ci/cd", new String[]{"cicd", "ci cd", "continuous integration"});
        ALIASES.put("data modeling", new String[]{"data modelling", "dimensional modeling"});
    }

    // ── Procedural fallback ──────────────────────────────────────────────────

    private static final String[] TEMPLATES_STUDENT = {
            "Explain %s as you would to a classmate who has never used it — then give a small example.",
            "What is %s used for, and what would go wrong if a team ignored it?",
            "Describe a course project or assignment where %s came up. What did you learn?",
            "What are the three most important things a beginner should understand about %s?",
            "How would you practise %s this week to get measurably better at it?"
    };

    private static final String[] TEMPLATES_PROFESSIONAL = {
            "Walk me through how you would approach %s in a %s role — what does \"good\" look like?",
            "Describe a real production problem you hit with %s and how you diagnosed it.",
            "Where do teams most often get %s wrong, and what would you do differently?",
            "What trade-offs matter most when applying %s at scale?",
            "How would you teach %s to a mid-level teammate, and what would you expect them to get wrong first?"
    };

    private static final Map<String, String[]> TOPICS_BY_ROLE = new LinkedHashMap<>();

    static {
        TOPICS_BY_ROLE.put("frontend", new String[]{
                "component state management", "rendering performance", "accessibility", "CSS architecture",
                "browser networking", "front-end testing"});
        TOPICS_BY_ROLE.put("backend", new String[]{
                "API design", "transactional consistency", "caching", "database indexing",
                "concurrency control", "observability"});
        TOPICS_BY_ROLE.put("full stack", new String[]{
                "end-to-end feature delivery", "authentication flows", "data modelling",
                "deployment pipelines", "performance budgeting", "error handling across tiers"});
        TOPICS_BY_ROLE.put("data engineer", new String[]{
                "pipeline reliability", "partitioning strategy", "data quality checks",
                "streaming vs batch", "warehouse modelling", "cost control"});
        TOPICS_BY_ROLE.put("devops", new String[]{
                "container orchestration", "zero-downtime deploys", "infrastructure as code",
                "alerting strategy", "secret management", "capacity planning"});
        TOPICS_BY_ROLE.put("ml", new String[]{
                "feature pipelines", "model evaluation", "drift monitoring",
                "training-serving consistency", "experimentation", "inference latency"});
    }

    private static final String[] GENERIC_TOPICS = {
            "technical decision making", "debugging production issues", "working with stakeholders",
            "code review culture", "technical debt", "incident response"};

    private Question variantFor(String role, Difficulty difficulty, List<String> skills, Level level, int variant) {
        // Prefer the student's own skills as the topic; fall back to role topics.
        String topic;
        if (!skills.isEmpty()) {
            topic = titleCase(skills.get((variant - 1) % skills.size()));
        } else {
            String[] topics = topicsFor(role);
            topic = topics[(variant - 1) % topics.length];
        }
        String[] templates = level == Level.PROFESSIONAL ? TEMPLATES_PROFESSIONAL : TEMPLATES_STUDENT;
        String template = templates[(variant - 1) % templates.length];
        String text = template.formatted(topic, role);
        String skillsForQuestion = skills.isEmpty() ? topic : String.join(",", skills.subList(0, Math.min(3, skills.size())));
        return new Question(
                text,
                role,
                titleCase(topic),
                difficulty,
                genericKeywords(topic),
                "A strong answer explains " + topic + " in their own words, gives a concrete example"
                        + (level == Level.PROFESSIONAL ? ", and names the trade-offs they weighed." : " from their own experience."),
                QuestionSource.AI,
                skillsForQuestion);
    }

    private static String[] topicsFor(String role) {
        if (role == null) {
            return GENERIC_TOPICS;
        }
        String key = role.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : TOPICS_BY_ROLE.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return GENERIC_TOPICS;
    }

    private static String genericKeywords(String topic) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : topic.split("\\s+")) {
            if (w.length() > 3) {
                words.add(w.toLowerCase(Locale.ROOT));
            }
        }
        words.add("example");
        words.add("why");
        return String.join(",", words);
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("\\s+")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
