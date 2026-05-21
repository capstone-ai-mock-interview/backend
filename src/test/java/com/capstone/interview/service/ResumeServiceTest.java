package com.capstone.interview.service;

import com.capstone.interview.entity.Member;
import com.capstone.interview.entity.Resume;
import com.capstone.interview.repository.MemberRepository;
import com.capstone.interview.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock PdfParserService pdfParserService;
    @Mock ResumePreprocessorService resumePreprocessorService;
    @Mock ResumeRepository resumeRepository;
    @Mock MemberRepository memberRepository;

    @InjectMocks ResumeService resumeService;

    @Test
    void deleteResume_marksResumeDeletedWithoutPhysicalDelete() {
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

        when(memberRepository.findByLoginId("user")).thenReturn(Optional.of(member));
        when(resumeRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(resume));

        resumeService.deleteResume("user", 7L);

        assertNotNull(resume.getDeletedAt());
        verify(resumeRepository, never()).delete(resume);
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
