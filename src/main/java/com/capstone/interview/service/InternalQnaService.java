package com.capstone.interview.service;

import com.capstone.interview.dto.InternalQnaRequest;
import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewQna;
import com.capstone.interview.event.QnaSavedEvent;
import com.capstone.interview.exception.SessionNotFoundException;
import com.capstone.interview.repository.InterviewQnaRepository;
import com.capstone.interview.repository.InterviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalQnaService {

    private final InterviewRepository interviewRepository;
    private final InterviewQnaRepository interviewQnaRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Upserts Q+A sent by the Agent. The unique key is (interview, sequenceNumber).
     */
    @Transactional
    public void upsertQna(String sessionId, InternalQnaRequest request) {
        Interview interview = interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("존재하지 않는 세션입니다. " + sessionId));

        InterviewQna existing = interviewQnaRepository
                .findByInterviewAndSequenceNumber(interview, request.turnNumber())
                .orElse(null);

        Long respondentId = resolveRespondentId(interview, request);

        if (existing != null) {
            if (respondentId != null) {
                existing.setRespondentMemberId(respondentId);
            }
            if (request.question() != null) {
                existing.updateQuestion(
                        request.question(),
                        request.intent(),
                        request.isFollowUp() != null && request.isFollowUp()
                );
            }
            if (request.answer() != null) {
                existing.updateAnswer(request.answer());
            }
            if (hasAnswerAnalysis(request)) {
                existing.updateAnswerAnalysis(
                        request.answerSummary() != null
                                ? toJson(request.answerSummary())
                                : existing.getAnswerSummary(),
                        request.followUpDecision() != null
                                ? request.followUpDecision()
                                : existing.getFollowUpDecision(),
                        request.focusPoint() != null
                                ? request.focusPoint()
                                : existing.getFocusPoint()
                );
            }
            log.info("[QnA upsert] updated sessionId={}, turn={}", sessionId, request.turnNumber());
        } else {
            InterviewQna newQna = InterviewQna.builder()
                    .interview(interview)
                    .sequenceNumber(request.turnNumber())
                    .questionContent(request.question())
                    .answerContent(request.answer() != null ? request.answer() : "")
                    .isFollowUp(request.isFollowUp() != null && request.isFollowUp())
                    .intent(request.intent())
                    .answerSummary(toJson(request.answerSummary()))
                    .followUpDecision(request.followUpDecision())
                    .focusPoint(request.focusPoint())
                    .respondentMemberId(respondentId)
                    .build();
            interviewQnaRepository.save(newQna);
            log.info("[QnA insert] created sessionId={}, turn={}", sessionId, request.turnNumber());
        }

        if (request.turnNumber() != null && request.answerSummary() != null) {
            eventPublisher.publishEvent(new QnaSavedEvent(sessionId, request.turnNumber()));
        }
    }

    private Long resolveRespondentId(Interview interview, InternalQnaRequest request) {
        if (request.respondentMemberId() != null) {
            return request.respondentMemberId();
        }
        if (!interview.isGroupMode() && interview.getMember() != null) {
            return interview.getMember().getId();
        }
        if (interview.isGroupMode()) {
            log.warn("[QnA upsert] group session missing respondentMemberId sessionId={}", interview.getSessionId());
        }
        return null;
    }

    private boolean hasAnswerAnalysis(InternalQnaRequest request) {
        return request.answerSummary() != null
                || request.followUpDecision() != null
                || request.focusPoint() != null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("answerSummary JSON serialization failed", e);
        }
    }
}
