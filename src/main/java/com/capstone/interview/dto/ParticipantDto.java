package com.capstone.interview.dto;

public record ParticipantDto(
    Long memberId,
    String name,
    String loginId,
    String role,
    boolean ready,
    String identity
) {}
