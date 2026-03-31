package com.capstone.interview.dto;

import java.util.List;

/** 이력서 내 트러블슈팅 경험 */
public record TroubleshootingInfo(
    String problem,
    String solution,
    List<String> techInvolved
) {}
