package com.capstone.interview.dto;

import java.util.List;

public record LobbyResponse(
    boolean success,
    LobbyResponse.Data data
) {
    public record Data(
        String sessionId,
        String status,
        String mode,
        int maxParticipants,
        int currentParticipants,
        int readyCount,
        boolean allReady,
        String myRole,
        String myIdentity,
        boolean myReady,
        List<ParticipantDto> participants,
        SessionCreateResponse.LiveKitInfo livekit
    ) {}
}
