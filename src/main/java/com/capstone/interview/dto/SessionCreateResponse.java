package com.capstone.interview.dto;

public record SessionCreateResponse(
    boolean success,
    SessionCreateResponse.Data data
) {
    public record Data(
        String sessionId,
        LiveKitInfo livekit
    ) {}

    public record LiveKitInfo(
        String roomName,
        String url,
        String accessToken
    ) {}
}
