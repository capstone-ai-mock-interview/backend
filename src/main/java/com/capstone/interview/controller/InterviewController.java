package com.capstone.interview.controller;

import com.capstone.interview.dto.JoinSessionRequest;
import com.capstone.interview.dto.JoinSessionResponse;
import com.capstone.interview.dto.LobbyResponse;
import com.capstone.interview.dto.NextTurnRequest;
import com.capstone.interview.dto.NextTurnResponse;
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

    @PostMapping("/{sessionId}/next")
    public ResponseEntity<NextTurnResponse> nextTurn(
            @PathVariable String sessionId,
            @RequestBody NextTurnRequest request) {
        log.info("[다음 질문 요청] sessionId={}, currentTurnNumber={}", sessionId, request.currentTurnNumber());

        NextTurnResponse response = interviewService.nextTurn(sessionId, request);
        log.info("[다음 질문 성공] sessionId={}, nextTurn={}", sessionId, response.data().turnNumber());
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

    @PostMapping("/{sessionId}/join")
    public ResponseEntity<JoinSessionResponse> joinSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) JoinSessionRequest request) {
        log.info("[세션 입장] sessionId={}", sessionId);
        JoinSessionResponse response = interviewService.joinSession(sessionId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/lobby")
    public ResponseEntity<LobbyResponse> getLobby(@PathVariable String sessionId) {
        return ResponseEntity.ok(interviewService.getLobby(sessionId));
    }

    @PatchMapping("/{sessionId}/participants/me/ready")
    public ResponseEntity<LobbyResponse> setReady(@PathVariable String sessionId) {
        log.info("[준비 완료] sessionId={}", sessionId);
        return ResponseEntity.ok(interviewService.setReady(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        log.info("[면접 기록 삭제 요청] sessionId={}", sessionId);

        interviewService.deleteSession(sessionId);
        log.info("[면접 기록 삭제 성공] sessionId={}", sessionId);
        return ResponseEntity.noContent().build();
    }
}
