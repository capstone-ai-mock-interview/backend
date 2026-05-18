package com.capstone.interview.service;

import com.capstone.interview.dto.*;
import com.capstone.interview.entity.*;
import com.capstone.interview.exception.ConflictException;
import com.capstone.interview.exception.InvalidStateException;
import com.capstone.interview.exception.SessionNotFoundException;
import com.capstone.interview.exception.UnauthorizedException;
import com.capstone.interview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final int ANSWER_TIME_LIMIT_SECONDS = 90;
    private static final int MAX_GROUP_PARTICIPANTS = 4;

    private final InterviewRepository interviewRepository;
    private final InterviewQnaRepository interviewQnaRepository;
    private final InterviewParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final ResumeRepository resumeRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final LiveKitService liveKitService;
    private final LiveKitRoomService liveKitRoomService;
    private final AgentDispatchService agentDispatchService;

    @Transactional
    public SessionCreateResponse createSession(SessionCreateRequest request) {
        if (request.jobField() == null || request.durationMinutes() == null) {
            throw new IllegalArgumentException("jobField와 durationMinutes는 필수입니다.");
        }

        int maxParticipants = normalizeMaxParticipants(request.maxParticipants());
        Member host = currentMember();

        Resume resume = resolveResume(request.resumeIds());
        CoverLetter coverLetter = resolveCoverLetter(request.coverLetter());

        String sessionId = "sess-" + UUID.randomUUID();
        String roomName = liveKitService.generateRoomName();
        int totalDurationSeconds = request.durationMinutes() * 60;
        InterviewMode mode = maxParticipants > 1 ? InterviewMode.GROUP : InterviewMode.SOLO;

        Interview interview = Interview.builder()
                .member(host)
                .resume(resume)
                .coverLetter(coverLetter)
                .category(request.jobField())
                .sessionId(sessionId)
                .roomName(roomName)
                .durationMinutes(request.durationMinutes())
                .maxParticipants(maxParticipants)
                .mode(mode)
                .build();

        if (mode == InterviewMode.GROUP) {
            interview.enterWaitingLobby();
            interviewRepository.save(interview);
            InterviewParticipant hostParticipant = InterviewParticipant.builder()
                    .interview(interview)
                    .member(host)
                    .role(ParticipantRole.HOST)
                    .resume(resume)
                    .ready(false)
                    .build();
            participantRepository.save(hostParticipant);

            String token = liveKitService.generateToken(roomName, hostParticipant.liveKitIdentity());
            return buildSessionResponse(interview, token, totalDurationSeconds);
        }

        interview.start();
        interviewRepository.save(interview);
        dispatchAgentAndInitTurn(interview, resume, coverLetter, request.jobField(),
                totalDurationSeconds, List.of(hostParticipantMeta(host, resume)));

        String token = liveKitService.generateToken(roomName, "user-" + host.getId());
        return buildSessionResponse(interview, token, totalDurationSeconds);
    }

    @Transactional
    public JoinSessionResponse joinSession(String sessionId, JoinSessionRequest request) {
        Interview interview = findInterviewOrThrow(sessionId);
        if (interview.getStatus() != InterviewStatus.WAITING) {
            throw new InvalidStateException("WAITING 상태의 세션만 입장할 수 있습니다. 현재: " + interview.getStatus());
        }
        if (!interview.isGroupMode()) {
            throw new InvalidStateException("그룹 면접 세션만 입장할 수 있습니다.");
        }

        Member member = currentMember();
        if (interview.getMember() != null && interview.getMember().getId().equals(member.getId())) {
            throw new ConflictException("호스트는 이미 세션에 참여 중입니다. 대기실로 이동하세요.");
        }

        Optional<InterviewParticipant> existing = participantRepository.findByInterviewAndMember(interview, member);
        if (existing.isPresent()) {
            InterviewParticipant p = existing.get();
            String token = liveKitService.generateToken(interview.getRoomName(), p.liveKitIdentity());
            return buildJoinResponse(interview, p, token);
        }

        long count = participantRepository.countByInterview(interview);
        if (count >= interview.getMaxParticipants()) {
            throw new ConflictException("면접 정원이 가득 찼습니다.");
        }

        Resume resume = null;
        if (request != null && request.resumeId() != null) {
            resume = resumeRepository.findById(request.resumeId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이력서입니다: " + request.resumeId()));
        }

        InterviewParticipant guest = InterviewParticipant.builder()
                .interview(interview)
                .member(member)
                .role(ParticipantRole.GUEST)
                .resume(resume)
                .ready(false)
                .build();
        participantRepository.save(guest);

        String token = liveKitService.generateToken(interview.getRoomName(), guest.liveKitIdentity());
        return buildJoinResponse(interview, guest, token);
    }

    @Transactional(readOnly = true)
    public LobbyResponse getLobby(String sessionId) {
        Interview interview = findInterviewOrThrow(sessionId);
        Member member = currentMember();
        InterviewParticipant me = findParticipantOrThrow(interview, member);

        List<InterviewParticipant> participants = participantRepository.findByInterviewOrderByJoinedAtAsc(interview);
        long readyCount = participantRepository.countByInterviewAndReadyTrue(interview);
        int currentCount = participants.size();
        boolean allReady = readyCount == interview.getMaxParticipants()
                && currentCount == interview.getMaxParticipants();

        String token = liveKitService.generateToken(interview.getRoomName(), me.liveKitIdentity());

        return new LobbyResponse(
                true,
                new LobbyResponse.Data(
                        interview.getSessionId(),
                        interview.getStatus().name(),
                        interview.getMode().name(),
                        interview.getMaxParticipants(),
                        currentCount,
                        (int) readyCount,
                        allReady,
                        me.getRole().name(),
                        me.liveKitIdentity(),
                        me.isReady(),
                        toParticipantDtos(participants),
                        new SessionCreateResponse.LiveKitInfo(
                                interview.getRoomName(),
                                liveKitService.getUrl(),
                                token
                        )
                )
        );
    }

    @Transactional
    public LobbyResponse setReady(String sessionId) {
        Interview interview = findInterviewOrThrow(sessionId);
        if (interview.getStatus() != InterviewStatus.WAITING) {
            throw new InvalidStateException("WAITING 상태에서만 준비할 수 있습니다. 현재: " + interview.getStatus());
        }

        Member member = currentMember();
        InterviewParticipant me = findParticipantOrThrow(interview, member);
        me.markReady();
        participantRepository.save(me);

        tryAutoStart(interview);

        return getLobby(sessionId);
    }

    @Transactional
    public NextTurnResponse nextTurn(String sessionId, NextTurnRequest request) {
        Interview interview = findInterviewOrThrow(sessionId);
        verifyHost(interview);
        verifyInProgress(interview);

        int currentTurn = interviewQnaRepository.countByInterview(interview);
        if (request.currentTurnNumber() != null && !request.currentTurnNumber().equals(currentTurn)) {
            log.warn("[/next] 턴 번호 불일치. 클라이언트={}, 서버={}", request.currentTurnNumber(), currentTurn);
        }

        int nextTurnNumber = currentTurn + 1;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(ANSWER_TIME_LIMIT_SECONDS);

        Long speakerId = interview.getCurrentSpeakerMemberId();
        if (speakerId == null && interview.getMember() != null) {
            speakerId = interview.getMember().getId();
        }

        InterviewQna nextQna = InterviewQna.builder()
                .interview(interview)
                .sequenceNumber(nextTurnNumber)
                .questionContent("")
                .answerContent("")
                .startedAt(now)
                .expiresAt(expiresAt)
                .respondentMemberId(speakerId)
                .build();
        interviewQnaRepository.save(nextQna);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("turnNumber", nextTurnNumber);
            if (speakerId != null) {
                payload.put("targetIdentity", "user-" + speakerId);
            }
            Map<String, Object> message = Map.of(
                    "type", "NEXT",
                    "payload", payload
            );
            liveKitRoomService.sendData(interview.getRoomName(), message);
        } catch (Exception e) {
            log.error("[/next] sendData 실패, 턴 증가를 롤백합니다. sessionId={}", sessionId, e);
            interviewQnaRepository.delete(nextQna);
            throw new RuntimeException("Agent에 다음 질문 신호 전송에 실패했습니다. 다시 시도해주세요.");
        }

        return new NextTurnResponse(
                true,
                new NextTurnResponse.Data(nextTurnNumber, now, expiresAt)
        );
    }

    @Transactional
    public SessionEndResponse endSession(String sessionId, String reason) {
        Interview interview = findInterviewOrThrow(sessionId);
        if (interview.isGroupMode()) {
            verifyHost(interview);
        } else {
            verifyOwner(interview);
        }
        verifyInProgress(interview);

        try {
            Map<String, Object> message = Map.of(
                    "type", "END",
                    "payload", Map.of("reason", reason != null ? reason : "USER_STOP")
            );
            liveKitRoomService.sendData(interview.getRoomName(), message);
        } catch (Exception e) {
            log.warn("[/end] sendData(END) 실패, deleteRoom으로 정리합니다. sessionId={}", sessionId);
        }

        liveKitRoomService.deleteRoom(interview.getRoomName());
        interview.complete();
        interviewRepository.save(interview);

        return new SessionEndResponse(
                true,
                "면접이 종료되었습니다. AI 피드백을 생성 중입니다.",
                new SessionEndResponse.Data(interview.getStatus().name())
        );
    }

    @Transactional
    protected void tryAutoStart(Interview interview) {
        if (interview.getStatus() != InterviewStatus.WAITING) {
            return;
        }

        long readyCount = participantRepository.countByInterviewAndReadyTrue(interview);
        long participantCount = participantRepository.countByInterview(interview);
        if (readyCount < interview.getMaxParticipants()
                || participantCount < interview.getMaxParticipants()) {
            return;
        }

        List<InterviewParticipant> participants =
                participantRepository.findByInterviewOrderByJoinedAtAsc(interview);
        InterviewParticipant firstSpeaker = participants.get(0);
        interview.setCurrentSpeakerMemberId(firstSpeaker.getMember().getId());
        interview.start();
        interviewRepository.save(interview);

        List<Map<String, Object>> participantMeta = buildParticipantMetadata(participants);
        String resumeText = interview.getResume() != null ? interview.getResume().getOriginalText() : "";
        String coverLetterText = interview.getCoverLetter() != null
                ? interview.getCoverLetter().getOriginalText() : "";

        try {
            agentDispatchService.dispatchGroup(
                    interview.getRoomName(),
                    interview.getSessionId(),
                    interview.getCategory(),
                    resumeText,
                    coverLetterText,
                    interview.getDurationMinutes() * 60,
                    ANSWER_TIME_LIMIT_SECONDS,
                    interview.getMaxParticipants(),
                    participantMeta
            );
        } catch (Exception e) {
            log.error("[그룹 시작] Agent dispatch 실패 sessionId={}", interview.getSessionId(), e);
            interview.fail();
            interviewRepository.save(interview);
            throw new RuntimeException("Agent dispatch에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        initFirstTurn(interview, firstSpeaker.getMember().getId());
        sendStartData(interview, participants, firstSpeaker);
    }

    private void dispatchAgentAndInitTurn(Interview interview, Resume resume, CoverLetter coverLetter,
                                          String jobField, int totalDurationSeconds,
                                          List<Map<String, Object>> participantMeta) {
        String resumeText = resume != null ? resume.getOriginalText() : "";
        String coverLetterText = coverLetter != null ? coverLetter.getOriginalText() : "";

        try {
            if (interview.isGroupMode()) {
                agentDispatchService.dispatchGroup(
                        interview.getRoomName(), interview.getSessionId(), jobField,
                        resumeText, coverLetterText, totalDurationSeconds,
                        ANSWER_TIME_LIMIT_SECONDS, interview.getMaxParticipants(), participantMeta);
            } else {
                agentDispatchService.dispatch(
                        interview.getRoomName(), interview.getSessionId(), jobField,
                        resumeText, coverLetterText, totalDurationSeconds, ANSWER_TIME_LIMIT_SECONDS);
            }
        } catch (Exception e) {
            log.error("[세션 생성] Agent dispatch 실패 sessionId={}", interview.getSessionId(), e);
            interview.fail();
            throw new RuntimeException("Agent dispatch에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        Long respondentId = interview.getMember() != null ? interview.getMember().getId() : null;
        initFirstTurn(interview, respondentId);
    }

    private void initFirstTurn(Interview interview, Long respondentMemberId) {
        LocalDateTime now = LocalDateTime.now();
        InterviewQna firstTurn = InterviewQna.builder()
                .interview(interview)
                .sequenceNumber(1)
                .questionContent("")
                .answerContent("")
                .startedAt(now)
                .expiresAt(now.plusSeconds(ANSWER_TIME_LIMIT_SECONDS))
                .respondentMemberId(respondentMemberId)
                .build();
        interviewQnaRepository.save(firstTurn);
    }

    private void sendStartData(Interview interview, List<InterviewParticipant> participants,
                               InterviewParticipant firstSpeaker) {
        List<Map<String, Object>> payloadParticipants = participants.stream()
                .map(p -> Map.<String, Object>of(
                        "memberId", p.getMember().getId(),
                        "identity", p.liveKitIdentity(),
                        "name", p.getMember().getName()
                ))
                .toList();

        Map<String, Object> message = Map.of(
                "type", "START",
                "payload", Map.of(
                        "participants", payloadParticipants,
                        "currentSpeakerIndex", 0,
                        "targetIdentity", firstSpeaker.liveKitIdentity()
                )
        );
        try {
            liveKitRoomService.sendData(interview.getRoomName(), message);
        } catch (Exception e) {
            log.warn("[START] sendData 실패 sessionId={}", interview.getSessionId(), e);
        }
    }

    private List<Map<String, Object>> buildParticipantMetadata(List<InterviewParticipant> participants) {
        List<Map<String, Object>> meta = new ArrayList<>();
        for (InterviewParticipant p : participants) {
            String resumeText = p.getResume() != null ? p.getResume().getOriginalText() : "";
            if (resumeText.isBlank() && p.getRole() == ParticipantRole.HOST
                    && p.getInterview().getResume() != null) {
                resumeText = p.getInterview().getResume().getOriginalText();
            }
            meta.add(Map.of(
                    "memberId", p.getMember().getId(),
                    "identity", p.liveKitIdentity(),
                    "name", p.getMember().getName(),
                    "resumeText", resumeText != null ? resumeText : ""
            ));
        }
        return meta;
    }

    private Map<String, Object> hostParticipantMeta(Member host, Resume resume) {
        return Map.of(
                "memberId", host.getId(),
                "identity", "user-" + host.getId(),
                "name", host.getName(),
                "resumeText", resume != null && resume.getOriginalText() != null ? resume.getOriginalText() : ""
        );
    }

    private SessionCreateResponse buildSessionResponse(Interview interview, String token, int totalDurationSeconds) {
        return new SessionCreateResponse(
                true,
                new SessionCreateResponse.Data(
                        interview.getSessionId(),
                        new SessionCreateResponse.LiveKitInfo(
                                interview.getRoomName(),
                                liveKitService.getUrl(),
                                token
                        ),
                        ANSWER_TIME_LIMIT_SECONDS,
                        totalDurationSeconds,
                        interview.getMode().name(),
                        interview.getMaxParticipants(),
                        interview.getStatus().name()
                )
        );
    }

    private JoinSessionResponse buildJoinResponse(Interview interview, InterviewParticipant participant, String token) {
        return new JoinSessionResponse(
                true,
                new JoinSessionResponse.Data(
                        interview.getSessionId(),
                        new SessionCreateResponse.LiveKitInfo(
                                interview.getRoomName(),
                                liveKitService.getUrl(),
                                token
                        ),
                        interview.getMode().name(),
                        interview.getStatus().name(),
                        participant.getRole().name(),
                        participant.liveKitIdentity(),
                        interview.getMaxParticipants()
                )
        );
    }

    private List<ParticipantDto> toParticipantDtos(List<InterviewParticipant> participants) {
        return participants.stream()
                .map(p -> new ParticipantDto(
                        p.getMember().getId(),
                        p.getMember().getName(),
                        p.getMember().getLoginId(),
                        p.getRole().name(),
                        p.isReady(),
                        p.liveKitIdentity()
                ))
                .toList();
    }

    private int normalizeMaxParticipants(Integer maxParticipants) {
        int value = maxParticipants != null ? maxParticipants : 1;
        if (value < 1 || value > MAX_GROUP_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "maxParticipants는 1~" + MAX_GROUP_PARTICIPANTS + " 사이여야 합니다.");
        }
        return value;
    }

    private Resume resolveResume(Long resumeId) {
        if (resumeId == null) return null;
        return resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이력서입니다: " + resumeId));
    }

    private CoverLetter resolveCoverLetter(Long coverLetterId) {
        if (coverLetterId == null) return null;
        return coverLetterRepository.findById(coverLetterId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자기소개서입니다: " + coverLetterId));
    }

    private Member currentMember() {
        String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UnauthorizedException("인증된 사용자를 찾을 수 없습니다."));
    }

    private Interview findInterviewOrThrow(String sessionId) {
        return interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("존재하지 않는 세션입니다: " + sessionId));
    }

    private InterviewParticipant findParticipantOrThrow(Interview interview, Member member) {
        return participantRepository.findByInterviewAndMember(interview, member)
                .orElseThrow(() -> new UnauthorizedException("이 면접 세션의 참가자가 아닙니다."));
    }

    private void verifyOwner(Interview interview) {
        String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = interview.getMember();
        if (member == null || !member.getLoginId().equals(loginId)) {
            throw new UnauthorizedException("본인의 면접 세션만 제어할 수 있습니다.");
        }
    }

    private void verifyHost(Interview interview) {
        Member member = currentMember();
        InterviewParticipant participant = findParticipantOrThrow(interview, member);
        if (participant.getRole() != ParticipantRole.HOST) {
            throw new UnauthorizedException("호스트만 이 작업을 수행할 수 있습니다.");
        }
    }

    private void verifyInProgress(Interview interview) {
        if (interview.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new InvalidStateException(
                    "IN_PROGRESS 상태에서만 가능합니다. 현재: " + interview.getStatus());
        }
    }

    /**
     * 면접 기록을 삭제한다. 본인의 면접 기록만 삭제 가능.
     * 관련 QnA 데이터도 함께 삭제된다.
     */
    @Transactional
    public void deleteSession(String sessionId) {
        Interview interview = findInterviewOrThrow(sessionId);
        verifyOwner(interview);

        interviewQnaRepository.deleteAllByInterview(interview);
        interviewRepository.delete(interview);
        log.info("[면접 기록 삭제] sessionId={}", sessionId);
    }

    public Interview findInterviewForFeedback(String sessionId) {
        return findInterviewOrThrow(sessionId);
    }

    public InterviewParticipant findParticipantForMember(Interview interview, Member member) {
        if (interview.isGroupMode()) {
            return findParticipantOrThrow(interview, member);
        }
        return null;
    }

    public Member getCurrentMember() {
        return currentMember();
    }
}
