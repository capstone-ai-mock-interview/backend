package com.capstone.interview.controller;

import com.capstone.interview.dto.FeedbackListDto;
import com.capstone.interview.dto.FeedbackResponse;
import com.capstone.interview.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [피드백 전용 컨트롤러]
 * 평가(Evaluation)와 분리되어, 오직 면접 결과 조회만 담당합니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/interviews")
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 면접 결과 피드백 상세 조회
     * GET /v1/interviews/feedback/{sessionId}
     * * @return 개별평가, 개별모범답안, 전체 피드백이 모두 담긴 FeedbackResponse
     */
    @GetMapping("/feedback/{sessionId}")
    public ResponseEntity<FeedbackResponse> getFeedback(
            @PathVariable String sessionId,
            Authentication authentication) {
        log.info("[피드백 조회 시작] sessionId: {}", sessionId);
        String loginId = authentication.getName();
        FeedbackResponse response = feedbackService.getFeedback(sessionId, loginId);
        log.info("[피드백 조회 완료] sessionId: {}", sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * 면접 기록 목록 조회 (마이페이지)
     * GET /v1/interviews/feedbackList
     * JWT 토큰에서 회원 정보를 추출하여 본인의 면접 기록만 조회
     */
    @GetMapping("/feedbackList")
    public ResponseEntity<List<FeedbackListDto>> getFeedbackList(Authentication authentication) {
        String loginId = authentication.getName();
        List<FeedbackListDto> list = feedbackService.getFeedbackList(loginId);
        return ResponseEntity.ok(list);
    }

}
