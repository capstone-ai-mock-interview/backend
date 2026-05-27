package com.capstone.interview.service;

import com.capstone.interview.config.EvaluationQueueProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "evaluation.dispatcher", havingValue = "sqs", matchIfMissing = true)
public class SqsEvaluationPublisher implements EvaluationRequestDispatcher {

    private final SqsClient sqsClient;
    private final EvaluationQueueProperties queueProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void requestTurnEvaluation(String sessionId, Integer turnNumber) {
        if (turnNumber == null) {
            log.warn("[turn evaluation queue skipped] turnNumber is null sessionId={}", sessionId);
            return;
        }
        publish(queueProperties.turnUrl(), queueProperties.turnName(), Map.of(
                "type", "TURN_EVALUATION",
                "sessionId", sessionId,
                "turnNumber", turnNumber
        ), "turn", sessionId);
    }

    @Override
    public void requestSessionEvaluation(String sessionId) {
        publish(queueProperties.sessionUrl(), queueProperties.sessionName(), Map.of(
                "type", "SESSION_EVALUATION",
                "sessionId", sessionId
        ), "session", sessionId);
    }

    private void publish(String configuredQueueUrl, String queueName,
                         Map<String, Object> message, String kind, String sessionId) {
        String queueUrl = resolveQueueUrl(configuredQueueUrl, queueName, kind, sessionId);
        if (queueUrl == null) {
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(message);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
            log.info("[{} evaluation queued] sessionId={}", kind, sessionId);
        } catch (JsonProcessingException e) {
            log.error("[{} evaluation queue serialization failed] sessionId={}", kind, sessionId, e);
        } catch (Exception e) {
            log.warn("[{} evaluation queue publish failed] sessionId={}", kind, sessionId, e);
        }
    }

    private String resolveQueueUrl(String configuredQueueUrl, String queueName, String kind, String sessionId) {
        if (configuredQueueUrl != null && !configuredQueueUrl.isBlank()) {
            return configuredQueueUrl;
        }
        if (queueName == null || queueName.isBlank()) {
            log.warn("[{} evaluation queue skipped] queue url/name is empty sessionId={}", kind, sessionId);
            return null;
        }
        try {
            return sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build()).queueUrl();
        } catch (Exception e) {
            log.warn("[{} evaluation queue url lookup failed] queueName={}, sessionId={}",
                    kind, queueName, sessionId, e);
            return null;
        }
    }
}
