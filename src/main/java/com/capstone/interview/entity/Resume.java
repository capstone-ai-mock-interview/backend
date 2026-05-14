package com.capstone.interview.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
@Getter
@NoArgsConstructor
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "file_url", length = 1024)
    private String fileUrl;

    @Column(columnDefinition = "text")
    private String keywords;

    // TODO: pgvector 타입 지원 시 vector(1536)으로 교체 (현재 JPA 기본 미지원)
    @Transient
    private float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Resume(Member member, String title, String originalText, String fileUrl, String keywords) {
        this.member = member;
        this.title = title;
        this.originalText = originalText;
        this.fileUrl = fileUrl;
        this.keywords = keywords;
    }

    public void updateOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public void updateFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public void updateKeywords(String keywords) {
        this.keywords = keywords;
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
