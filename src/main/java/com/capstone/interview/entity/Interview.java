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

    @Column(name = "room_name", length = 50)
    private String roomName;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewMode mode = InterviewMode.SOLO;

    @Column(name = "current_speaker_member_id")
    private Long currentSpeakerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status = InterviewStatus.READY;

    @Column(name = "total_feedback", columnDefinition = "TEXT")
    private String totalFeedback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Interview(Member member, Resume resume, CoverLetter coverLetter,
                     String category, String sessionId, String roomName, Integer durationMinutes,
                     Integer maxParticipants, InterviewMode mode) {
        this.member = member;
        this.resume = resume;
        this.coverLetter = coverLetter;
        this.category = category;
        this.sessionId = sessionId;
        this.roomName = roomName;
        this.durationMinutes = durationMinutes;
        this.maxParticipants = maxParticipants != null ? maxParticipants : 1;
        this.mode = mode != null ? mode : InterviewMode.SOLO;
        this.status = InterviewStatus.READY;
    }

    public void enterWaitingLobby() {
        if (this.status != InterviewStatus.READY) {
            throw new IllegalStateException("READY 상태에서만 대기실로 전환할 수 있습니다. 현재: " + this.status);
        }
        this.status = InterviewStatus.WAITING;
    }

    public void start() {
        if (this.mode == InterviewMode.GROUP) {
            if (this.status != InterviewStatus.WAITING) {
                throw new IllegalStateException("WAITING 상태에서만 시작할 수 있습니다. 현재: " + this.status);
            }
        } else if (this.status != InterviewStatus.READY) {
            throw new IllegalStateException("READY 상태에서만 시작할 수 있습니다. 현재: " + this.status);
        }
        this.status = InterviewStatus.IN_PROGRESS;
    }

    public boolean isGroupMode() {
        return this.mode == InterviewMode.GROUP;
    }

    public void setCurrentSpeakerMemberId(Long memberId) {
        this.currentSpeakerMemberId = memberId;
    }

    public void complete() {
        if (this.status != InterviewStatus.IN_PROGRESS) {
            throw new IllegalStateException("IN_PROGRESS 상태에서만 종료할 수 있습니다. 현재: " + this.status);
        }
        this.status = InterviewStatus.COMPLETED;
    }

    public void fail() {
        this.status = InterviewStatus.FAILED;
    }

    public void clearResume() {
        this.resume = null;
    }

    //EvaluationService에서 total_feedback 저장을 위해 호출
    public void saveTotalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
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
