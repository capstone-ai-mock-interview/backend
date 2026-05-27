package com.capstone.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "evaluation.queue")
public record EvaluationQueueProperties(
        String turnUrl,
        String turnName,
        String sessionUrl,
        String sessionName
) {
}
