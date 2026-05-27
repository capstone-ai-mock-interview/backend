package com.capstone.interview.service;

public interface EvaluationRequestDispatcher {

    void requestTurnEvaluation(String sessionId, Integer turnNumber);

    void requestSessionEvaluation(String sessionId);
}
