package com.interviewiq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single interview question. Questions come either from the curated bank or
 * from the AI generator, and carry the rubric data used by the scorer.
 */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "question_text", nullable = false)
    private String text;

    @Column(name = "role_name", nullable = false, length = 120)
    private String role;

    @Column(length = 120)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private QuestionSource source = QuestionSource.BANK;

    /** Comma separated key concepts a strong answer should hit. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "expected_keywords")
    private String expectedKeywords;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "ideal_answer")
    private String idealAnswer;

    /** Comma-separated skill tags, e.g. "Java,Concurrency" — drives skill-based selection. */
    @Column(name = "skill_tags", length = 300)
    private String skillTags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Question() {
    }

    public Question(String text, String role, String topic, Difficulty difficulty,
                    String expectedKeywords, String idealAnswer, QuestionSource source) {
        this(text, role, topic, difficulty, expectedKeywords, idealAnswer, source, null);
    }

    public Question(String text, String role, String topic, Difficulty difficulty,
                    String expectedKeywords, String idealAnswer, QuestionSource source, String skillTags) {
        this.text = text;
        this.role = role;
        this.topic = topic;
        this.difficulty = difficulty;
        this.expectedKeywords = expectedKeywords;
        this.idealAnswer = idealAnswer;
        this.source = source;
        this.skillTags = skillTags;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public QuestionSource getSource() {
        return source;
    }

    public void setSource(QuestionSource source) {
        this.source = source;
    }

    public String getExpectedKeywords() {
        return expectedKeywords;
    }

    public void setExpectedKeywords(String expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
    }

    public String getIdealAnswer() {
        return idealAnswer;
    }

    public void setIdealAnswer(String idealAnswer) {
        this.idealAnswer = idealAnswer;
    }

    public String getSkillTags() {
        return skillTags;
    }

    public void setSkillTags(String skillTags) {
        this.skillTags = skillTags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
