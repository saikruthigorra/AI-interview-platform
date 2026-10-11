package com.interviewiq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(length = 190)
    private String headline;

    @Column(name = "target_role", length = 120)
    private String targetRole;

    /** Comma-separated skills the student claims, e.g. "React,TypeScript,CSS". */
    @Column(length = 500)
    private String skills;

    /** e.g. "B.Tech CSE · 2026" — shown on reports and used to pitch questions. */
    @Column(length = 190)
    private String education;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Level level = Level.STUDENT;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Candidate() {
    }

    public Candidate(String name, String email, String headline, String targetRole) {
        this.name = name;
        this.email = email;
        this.headline = headline;
        this.targetRole = targetRole;
    }

    public Candidate(String name, String email, String headline, String targetRole,
                     String skills, String education, Level level) {
        this(name, email, headline, targetRole);
        this.skills = skills;
        this.education = education;
        this.level = level == null ? Level.STUDENT : level;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
