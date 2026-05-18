package com.capstone.interview.dto;

public record SessionCreateResponse(
    boolean success,
    SessionCreateResponse.Data data
) {
    public record Data(
        String sessionId,
        LiveKitInfo livekit,
        int answerTimeLimitSeconds,
        int totalDurationSeconds,
        String mode,
        int maxParticipants,
        String status
    ) {}

    public record LiveKitInfo(
        String roomName,
        String url,
        String accessToken
    ) {}
}
