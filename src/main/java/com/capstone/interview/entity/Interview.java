package com.capstone.interview.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interviews")
@Getter
@NoArgsConstructor
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = true)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_letter_id")
    private CoverLetter coverLetter;

    @Column(name = "session_id", unique = true, nullable = false, length = 50)
    private String sessionId;

    @Column(nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status = InterviewStatus.READY;

    @Column(name = "total_feedback", columnDefinition = "TEXT")
    private String totalFeedback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

<<<<<<< feature/evaluationService
    /** 종합 피드백 저장 (EvaluationService에서 호출) */
    public void updateTotalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
=======
    @Builder
    public Interview(Member member, Resume resume, CoverLetter coverLetter, String category, String sessionId) {
        this.member = member;
        this.resume = resume;
        this.coverLetter = coverLetter;
        this.category = category;
        this.sessionId = sessionId;
        this.status = InterviewStatus.READY;
    }

    public void start() {
        if (this.status != InterviewStatus.READY) {
            throw new IllegalStateException("READY 상태에서만 시작할 수 있습니다. 현재: " + this.status);
        }
        this.status = InterviewStatus.IN_PROGRESS;
    }

    public void complete() {
        if (this.status != InterviewStatus.IN_PROGRESS) {
            throw new IllegalStateException("IN_PROGRESS 상태에서만 종료할 수 있습니다. 현재: " + this.status);
        }
        this.status = InterviewStatus.COMPLETED;
    }

    public void fail() {
        this.status = InterviewStatus.FAILED;
>>>>>>> main
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
