package com.capstone.interview.controller;

import com.capstone.interview.dto.EvaluationResponse;
import com.capstone.interview.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    /**
     * 면접 평가 API.
     * 면접 종료 후 호출하면 LLM이 각 질문-답변을 평가하고 피드백을 저장한다.
     *
     * POST /interviews/{interviewId}/evaluate
     */
    @PostMapping("/interviews/{interviewId}/evaluate")
    public ResponseEntity<EvaluationResponse> evaluate(@PathVariable Long interviewId) {
        EvaluationResponse response = evaluationService.evaluate(interviewId);
        return ResponseEntity.ok(response);
    }
}
