package com.capstone.interview.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"interview_id", "member_id"}))
@Getter
@NoArgsConstructor
public class InterviewParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    @Column(nullable = false)
    private boolean ready = false;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private Resume resume;

    @Column(name = "total_feedback", columnDefinition = "TEXT")
    private String totalFeedback;

    @Column(name = "overall_score", length = 20)
    private String overallScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public InterviewParticipant(Interview interview, Member member, ParticipantRole role,
                                Resume resume, boolean ready) {
        this.interview = interview;
        this.member = member;
        this.role = role;
        this.resume = resume;
        this.ready = ready;
        this.joinedAt = LocalDateTime.now();
    }

    public void markReady() {
        this.ready = true;
    }

    public void saveTotalFeedback(String totalFeedback, String overallScore) {
        this.totalFeedback = totalFeedback;
        this.overallScore = overallScore;
    }

    public boolean hasFeedback() {
        return totalFeedback != null;
    }

    public String liveKitIdentity() {
        return "user-" + member.getId();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
