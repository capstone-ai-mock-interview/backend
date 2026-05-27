package com.capstone.interview.service;

import com.capstone.interview.event.QnaSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class QnaSavedEvaluationListener {

    private final EvaluationRequestDispatcher evaluationRequestDispatcher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleQnaSaved(QnaSavedEvent event) {
        try {
            evaluationRequestDispatcher.requestTurnEvaluation(event.sessionId(), event.turnNumber());
        } catch (Exception e) {
            log.warn("[turn evaluation dispatch failed] sessionId={}, turn={}",
                    event.sessionId(), event.turnNumber(), e);
        }
    }
}
