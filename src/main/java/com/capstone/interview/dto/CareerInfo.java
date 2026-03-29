package com.capstone.interview.dto;

/** 이력서 내 경력 정보 */
public record CareerInfo(
    String company,
    String position,
    String period,
    String description
) {}
