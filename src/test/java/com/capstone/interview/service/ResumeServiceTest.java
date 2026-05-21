package com.capstone.interview.service;

import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewParticipant;
import com.capstone.interview.entity.Member;
import com.capstone.interview.entity.ParticipantRole;
import com.capstone.interview.entity.Resume;
import com.capstone.interview.repository.InterviewParticipantRepository;
import com.capstone.interview.repository.InterviewRepository;
import com.capstone.interview.repository.MemberRepository;
import com.capstone.interview.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock PdfParserService pdfParserService;
    @Mock ResumePreprocessorService resumePreprocessorService;
    @Mock ResumeRepository resumeRepository;
    @Mock MemberRepository memberRepository;
    @Mock InterviewRepository interviewRepository;
    @Mock InterviewParticipantRepository interviewParticipantRepository;

    @InjectMocks ResumeService resumeService;

    @Test
    void deleteResume_clearsInterviewAndParticipantReferencesBeforeDelete() {
        Member member = Member.builder()
                .loginId("user")
                .password("password")
                .name("User")
                .build();
        setId(member, 1L);

        Resume resume = Resume.builder()
                .member(member)
                .title("resume")
                .originalText("text")
                .build();
        setId(resume, 7L);

        Interview interview = Interview.builder()
                .member(member)
                .resume(resume)
                .category("BACKEND")
                .sessionId("session")
                .roomName("room")
                .durationMinutes(15)
                .build();

        InterviewParticipant participant = InterviewParticipant.builder()
                .interview(interview)
                .member(member)
                .role(ParticipantRole.HOST)
                .resume(resume)
                .ready(true)
                .build();

        when(memberRepository.findByLoginId("user")).thenReturn(Optional.of(member));
        when(resumeRepository.findById(7L)).thenReturn(Optional.of(resume));
        when(interviewRepository.findByResumeId(7L)).thenReturn(List.of(interview));
        when(interviewParticipantRepository.findByResumeId(7L)).thenReturn(List.of(participant));

        resumeService.deleteResume("user", 7L);

        assertNull(interview.getResume());
        assertNull(participant.getResume());
        verify(resumeRepository).delete(resume);
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
