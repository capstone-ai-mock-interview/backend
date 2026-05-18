package com.capstone.interview.dto;

public record JoinSessionResponse(
    boolean success,
    JoinSessionResponse.Data data
) {
    public record Data(
        String sessionId,
        SessionCreateResponse.LiveKitInfo livekit,
        String mode,
        String status,
        String role,
        String myIdentity,
        int maxParticipants
    ) {}
}
