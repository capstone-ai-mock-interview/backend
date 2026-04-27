package com.capstone.interview.controller;

import com.capstone.interview.dto.SessionCreateRequest;
import com.capstone.interview.dto.SessionCreateResponse;
import com.capstone.interview.dto.SessionEndRequest;
import com.capstone.interview.dto.SessionEndResponse;
import com.capstone.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/interviews/sessions")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    public ResponseEntity<SessionCreateResponse> createSession(@RequestBody SessionCreateRequest request) {
        log.info("[세션 생성 요청] jobField={}, resumeIds={}, coverLetter={}, durationMinutes={}",
                request.jobField(), request.resumeIds(), request.coverLetter(), request.durationMinutes());

        SessionCreateResponse response = interviewService.createSession(request);
        log.info("[세션 생성 성공] sessionId={}", response.data().sessionId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<SessionEndResponse> endSession(
            @PathVariable String sessionId,
            @RequestBody SessionEndRequest request) {
        log.info("[세션 종료 요청] sessionId={}, reason={}", sessionId, request.reason());

        SessionEndResponse response = interviewService.endSession(sessionId, request.reason());
        log.info("[세션 종료 성공] sessionId={}, status={}", sessionId, response.data().status());
        return ResponseEntity.ok(response);
    }
}
