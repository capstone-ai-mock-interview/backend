package com.capstone.interview.service;

import com.capstone.interview.dto.SessionCreateRequest;
import com.capstone.interview.dto.SessionCreateResponse;
import com.capstone.interview.dto.SessionEndResponse;
import com.capstone.interview.entity.CoverLetter;
import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.Resume;
import com.capstone.interview.repository.InterviewRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final LiveKitService liveKitService;
    private final EntityManager entityManager;

    @Transactional
    public SessionCreateResponse createSession(SessionCreateRequest request) {
        if (request.jobField() == null || request.durationMinutes() == null) {
            throw new IllegalArgumentException("jobField와 durationMinutes는 필수입니다.");
        }

        Resume resume = request.resumeIds() != null
                ? entityManager.getReference(Resume.class, request.resumeIds()) : null;
        CoverLetter coverLetter = request.coverLetter() != null
                ? entityManager.getReference(CoverLetter.class, request.coverLetter()) : null;

        String sessionId = "sess-" + UUID.randomUUID();

        Interview interview = Interview.builder()
                .member(null) // TODO: 인증 구현 시 SecurityContext에서 member 추출로 교체
                .resume(resume)
                .coverLetter(coverLetter)
                .category(request.jobField())
                .sessionId(sessionId)
                .build();

        interview.start();
        interviewRepository.save(interview);

        String roomName = liveKitService.generateRoomName();
        String token = liveKitService.generateToken(roomName, "user-" + interview.getId());

        return new SessionCreateResponse(
                true,
                new SessionCreateResponse.Data(
                        sessionId,
                        new SessionCreateResponse.LiveKitInfo(roomName, liveKitService.getUrl(), token)
                )
        );
    }

    @Transactional
    public SessionEndResponse endSession(String sessionId, String reason) {
        Interview interview = interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));

        interview.complete();

        return new SessionEndResponse(
                true,
                "면접이 종료되었습니다. AI 피드백을 생성 중입니다.",
                new SessionEndResponse.Data(interview.getStatus().name())
        );
    }
}
