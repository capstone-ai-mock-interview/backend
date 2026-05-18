package com.capstone.interview.service;

import com.capstone.interview.dto.*;
import com.capstone.interview.entity.*;
import com.capstone.interview.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupInterviewFlowTest {

    @Mock InterviewRepository interviewRepository;
    @Mock InterviewQnaRepository interviewQnaRepository;
    @Mock InterviewParticipantRepository participantRepository;
    @Mock MemberRepository memberRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock CoverLetterRepository coverLetterRepository;
    @Mock LiveKitService liveKitService;
    @Mock LiveKitRoomService liveKitRoomService;
    @Mock AgentDispatchService agentDispatchService;

    @InjectMocks InterviewService interviewService;

    private Member host;
    private Member guest;

    @BeforeEach
    void setUp() {
        host = Member.builder().loginId("host").password("p").name("Host").build();
        setId(host, 1L);
        guest = Member.builder().loginId("guest").password("p").name("Guest").build();
        setId(guest, 2L);

        when(liveKitService.generateRoomName()).thenReturn("room-test");
        when(liveKitService.getUrl()).thenReturn("wss://test");
        when(liveKitService.generateToken(anyString(), anyString())).thenReturn("token");
    }

    @Test
    void createGroupSession_staysWaitingWithoutDispatch() {
        loginAs(host);
        when(memberRepository.findByLoginId("host")).thenReturn(Optional.of(host));
        when(interviewRepository.save(any())).thenAnswer(inv -> {
            Interview i = inv.getArgument(0);
            setId(i, 10L);
            return i;
        });
        when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionCreateResponse response = interviewService.createSession(
                new SessionCreateRequest(null, null, "BACKEND", 15, 2));

        assertEquals("WAITING", response.data().status());
        assertEquals("GROUP", response.data().mode());
        assertEquals(2, response.data().maxParticipants());
        verify(agentDispatchService, never()).dispatchGroup(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyList());
    }

    @Test
    void autoStart_whenAllReady() {
        Interview interview = Interview.builder()
                .member(host)
                .category("BACKEND")
                .sessionId("sess-group-1")
                .roomName("room-1")
                .durationMinutes(15)
                .maxParticipants(2)
                .mode(InterviewMode.GROUP)
                .build();
        interview.enterWaitingLobby();
        setId(interview, 10L);

        InterviewParticipant hostP = InterviewParticipant.builder()
                .interview(interview).member(host).role(ParticipantRole.HOST).ready(false).build();
        InterviewParticipant guestP = InterviewParticipant.builder()
                .interview(interview).member(guest).role(ParticipantRole.GUEST).ready(false).build();

        loginAs(host);
        when(memberRepository.findByLoginId("host")).thenReturn(Optional.of(host));
        when(interviewRepository.findBySessionId("sess-group-1")).thenReturn(Optional.of(interview));
        when(participantRepository.findByInterviewAndMember(interview, host)).thenReturn(Optional.of(hostP));
        when(participantRepository.findByInterviewOrderByJoinedAtAsc(interview))
                .thenReturn(java.util.List.of(hostP, guestP));
        when(participantRepository.countByInterviewAndReadyTrue(interview)).thenReturn(2L);
        when(participantRepository.countByInterview(interview)).thenReturn(2L);
        when(interviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(interviewQnaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LobbyResponse lobby = interviewService.setReady("sess-group-1");

        assertEquals("IN_PROGRESS", lobby.data().status());
        verify(agentDispatchService).dispatchGroup(any(), eq("sess-group-1"), any(), any(), any(), anyInt(), anyInt(), eq(2), anyList());
        verify(liveKitRoomService).sendData(eq("room-1"), argThat(m -> "START".equals(m.get("type"))));
    }

    private void loginAs(Member member) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getLoginId(), null));
    }

    private static void setId(Object entity, Long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
