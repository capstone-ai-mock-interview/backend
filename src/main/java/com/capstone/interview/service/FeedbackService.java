package com.capstone.interview.service;

import com.capstone.interview.dto.FeedbackListDto;
import com.capstone.interview.dto.FeedbackResponse;
import com.capstone.interview.dto.QAPair;
import com.capstone.interview.entity.*;
import com.capstone.interview.exception.UnauthorizedException;
import com.capstone.interview.repository.InterviewParticipantRepository;
import com.capstone.interview.repository.InterviewQnaRepository;
import com.capstone.interview.repository.InterviewRepository;
import com.capstone.interview.repository.MemberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final InterviewRepository interviewRepository;
    private final InterviewQnaRepository interviewQnaRepository;
    private final InterviewParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FeedbackResponse getFeedback(String sessionId, String loginId) {
        Interview interview = interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 면접 세션입니다: " + sessionId));

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UnauthorizedException("회원 정보를 찾을 수 없습니다."));

        if (interview.isGroupMode()) {
            InterviewParticipant participant = participantRepository
                    .findByInterviewAndMember(interview, member)
                    .orElseThrow(() -> new UnauthorizedException("이 면접의 참가자가 아닙니다."));

            String rawFeedback = participant.getTotalFeedback();
            List<InterviewQna> qnas = interviewQnaRepository
                    .findByInterviewAndRespondentMemberIdOrderBySequenceNumberAsc(interview, member.getId());

            if (rawFeedback == null) {
                return FeedbackResponse.builder()
                        .success(false)
                        .totalFeedback(interview.getStatus() == InterviewStatus.COMPLETED
                                ? "AI 피드백을 생성 중입니다. 잠시 후 다시 시도해주세요."
                                : "면접이 아직 종료되지 않았습니다.")
                        .qaPairs(List.of())
                        .build();
            }

            if (qnas.isEmpty()) {
                return FeedbackResponse.builder()
                        .success(true)
                        .totalFeedback(rawFeedback)
                        .overallScore(extractOverallScore(rawFeedback))
                        .competencyChart(extractChartData(rawFeedback))
                        .qaPairs(List.of())
                        .build();
            }

            return buildFeedbackResponse(rawFeedback, qnas);
        }

        String rawFeedback = interview.getTotalFeedback();
        List<InterviewQna> qnas = interviewQnaRepository
                .findByInterviewOrderBySequenceNumberAsc(interview);

        if (rawFeedback == null) {
            return FeedbackResponse.builder()
                    .success(false)
                    .totalFeedback(interview.getStatus() == InterviewStatus.COMPLETED
                            ? "AI 피드백을 생성 중입니다. 잠시 후 다시 시도해주세요."
                            : "면접이 아직 종료되지 않았습니다.")
                    .qaPairs(List.of())
                    .build();
        }

        return buildFeedbackResponse(rawFeedback, qnas);
    }

    @Transactional(readOnly = true)
    public List<FeedbackListDto> getFeedbackList(String loginId) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UnauthorizedException("회원 정보를 찾을 수 없습니다."));

        List<FeedbackListDto> result = new ArrayList<>();

        List<Interview> owned = interviewRepository.findByMemberIdAndStatusOrderByCreatedAtDesc(
                member.getId(), InterviewStatus.COMPLETED);
        for (Interview interview : owned) {
            if (interview.isGroupMode()) {
                participantRepository.findByInterviewAndMember(interview, member)
                        .ifPresent(p -> result.add(toListDto(interview, p.getOverallScore())));
            } else {
                result.add(toListDto(interview, extractOverallScore(interview.getTotalFeedback())));
            }
        }

        List<InterviewParticipant> guestRecords =
                participantRepository.findByMemberIdOrderByJoinedAtDesc(member.getId());
        for (InterviewParticipant p : guestRecords) {
            Interview interview = p.getInterview();
            if (interview.getStatus() != InterviewStatus.COMPLETED) {
                continue;
            }
            if (interview.getMember() != null && interview.getMember().getId().equals(member.getId())) {
                continue;
            }
            result.add(toListDto(interview, p.getOverallScore()));
        }

        java.util.Map<String, FeedbackListDto> bySession = new java.util.LinkedHashMap<>();
        for (FeedbackListDto dto : result) {
            bySession.putIfAbsent(dto.sessionId(), dto);
        }
        return bySession.values().stream()
                .sorted(Comparator.comparing(FeedbackListDto::createdAt).reversed())
                .toList();
    }

    private FeedbackListDto toListDto(Interview interview, String overallScore) {
        return FeedbackListDto.builder()
                .sessionId(interview.getSessionId())
                .category(interview.getCategory())
                .overallScore(overallScore)
                .createdAt(interview.getCreatedAt())
                .build();
    }

    private FeedbackResponse buildFeedbackResponse(String rawFeedback, List<InterviewQna> qnas) {
        List<QAPair> qaPairs = qnas.stream()
                .map(qna -> QAPair.builder()
                        .sequenceNumber(qna.getSequenceNumber())
                        .questionContent(qna.getQuestionContent())
                        .answerContent(parseAnswerSummary(qna))
                        .modelAnswer(qna.getModelAnswer())
                        .individualFeedback(qna.getIndividualFeedback())
                        .isFollowUp(qna.isFollowUp())
                        .parentSequenceNumber(qna.getParent() != null ? qna.getParent().getSequenceNumber() : null)
                        .build())
                .toList();

        String onlyChart = extractChartData(rawFeedback);
        String onlyFeedback = rawFeedback.split("\n\n\\[SCORE\\]")[0];

        return FeedbackResponse.builder()
                .success(true)
                .totalFeedback(onlyFeedback)
                .overallScore(extractOverallScore(rawFeedback))
                .competencyChart(onlyChart)
                .qaPairs(qaPairs)
                .build();
    }

    private String extractChartData(String rawData) {
        if (rawData != null && rawData.contains("[CHART]")) {
            return rawData.substring(rawData.indexOf("[CHART]") + "[CHART]".length()).trim();
        }
        return "{}";
    }

    private String parseAnswerSummary(InterviewQna qna) {
        String summary = qna.getAnswerSummary();
        if (summary == null || summary.isBlank()) {
            return qna.getAnswerContent();
        }
        try {
            JsonNode node = objectMapper.readTree(summary);
            if (node.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : node) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(item.asText());
                }
                return sb.toString();
            }
            return summary;
        } catch (Exception e) {
            return summary;
        }
    }

    private String extractOverallScore(String rawFeedback) {
        if (rawFeedback == null || !rawFeedback.contains("[SCORE]")) return null;
        int start = rawFeedback.indexOf("[SCORE]") + "[SCORE]\n".length();
        int end = rawFeedback.indexOf("\n\n[CHART]", start);
        if (end == -1) end = rawFeedback.length();
        String score = rawFeedback.substring(start, end).trim();
        return score.isEmpty() ? null : score;
    }
}
