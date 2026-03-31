package com.capstone.interview.dto;

import java.util.List;

/** 이력서 내 프로젝트 정보 */
public record ProjectInfo(
    String name,
    String description,
    List<String> techUsed,
    String role,
    String troubleshooting
) {}
