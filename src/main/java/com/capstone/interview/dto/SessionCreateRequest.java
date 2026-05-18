package com.capstone.interview.dto;

public record SessionCreateRequest(
    Long resumeIds,         // optional
    Long coverLetter,       // optional
    String jobField,        // 필수
    Integer durationMinutes, // 필수
    Integer maxParticipants  // optional, default 1
) {}
