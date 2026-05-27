package com.capstone.interview.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "evaluation.dispatcher", havingValue = "direct")
public class DirectEvaluationDispatcher implements EvaluationRequestDispatcher {

    private final EvaluationService evaluationService;

    @Override
    public void requestTurnEvaluation(String sessionId, Integer turnNumber) {
        evaluationService.evaluateTurn(sessionId, turnNumber);
    }

    @Override
    public void requestSessionEvaluation(String sessionId) {
        evaluationService.evaluate(sessionId);
    }
}
