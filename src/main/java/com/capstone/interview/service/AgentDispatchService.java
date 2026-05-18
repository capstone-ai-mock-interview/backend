package com.capstone.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LiveKit Agent Dispatch 서비스.
 * Twirp HTTP API 를 사용하여 Agent Worker 에게 Job 을 배포한다.
 */
@Slf4j
@Service
public class AgentDispatchService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;
    private final String agentName;

    public AgentDispatchService(
            @Value("${livekit.url}") String livekitUrl,
            @Value("${livekit.api-key}") String apiKey,
            @Value("${livekit.api-secret}") String apiSecret,
            @Value("${livekit.agent-name:interviewer-agent}") String agentName,
            ObjectMapper objectMapper) {
        String httpUrl = livekitUrl.replace("wss://", "https://").replace("ws://", "http://");
        this.restClient = RestClient.builder().baseUrl(httpUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.agentName = agentName;
    }

    /**
     * Agent 를 dispatch 한다.
     */
    public void dispatch(String roomName, String sessionId, String jobRole,
                         String resumeText, String coverLetterText,
                         int totalDurationSeconds, int answerTimeLimitSeconds) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "SOLO");
        metadata.put("sessionId", sessionId);
        metadata.put("jobRole", jobRole);
        metadata.put("resumeText", resumeText != null ? resumeText : "");
        metadata.put("coverLetterText", coverLetterText != null ? coverLetterText : "");
        metadata.put("totalDurationSeconds", totalDurationSeconds);
        metadata.put("answerTimeLimitSeconds", answerTimeLimitSeconds);
        createDispatch(roomName, sessionId, metadata);
    }

    public void dispatchGroup(String roomName, String sessionId, String jobRole,
                              String resumeText, String coverLetterText,
                              int totalDurationSeconds, int answerTimeLimitSeconds,
                              int maxParticipants, List<Map<String, Object>> participants) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "GROUP");
        metadata.put("sessionId", sessionId);
        metadata.put("jobRole", jobRole);
        metadata.put("resumeText", resumeText != null ? resumeText : "");
        metadata.put("coverLetterText", coverLetterText != null ? coverLetterText : "");
        metadata.put("totalDurationSeconds", totalDurationSeconds);
        metadata.put("answerTimeLimitSeconds", answerTimeLimitSeconds);
        metadata.put("maxParticipants", maxParticipants);
        metadata.put("participants", participants);
        createDispatch(roomName, sessionId, metadata);
    }

    private void createDispatch(String roomName, String sessionId, Map<String, Object> metadata) {
        try {
            String metadataJson = objectMapper.writeValueAsString(metadata);

            // Twirp request body
            Map<String, String> requestBody = new LinkedHashMap<>();
            requestBody.put("room", roomName);
            requestBody.put("agent_name", agentName);
            requestBody.put("metadata", metadataJson);

            // roomAdmin 권한의 JWT 생성
            String token = generateRoomAdminToken(roomName);

            log.info("[Agent Dispatch] room={}, agent={}, sessionId={}", roomName, agentName, sessionId);

            restClient.post()
                    .uri("/twirp/livekit.AgentDispatchService/CreateDispatch")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .toBodilessEntity();

            log.info("[Agent Dispatch 성공] sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[Agent Dispatch 실패] sessionId={}, error={}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Agent dispatch 실패: " + e.getMessage(), e);
        }
    }

    private String generateRoomAdminToken(String roomName) {
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setName("backend-service");
        token.setIdentity("backend-service");
        token.addGrants(new RoomAdmin(true), new RoomName(roomName));
        return token.toJwt();
    }
}
