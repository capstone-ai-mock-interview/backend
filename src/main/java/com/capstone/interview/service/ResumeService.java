package com.capstone.interview.service;

import com.capstone.interview.dto.ResumeResponse;
import com.capstone.interview.dto.ResumeUploadRequest;
import com.capstone.interview.entity.Member;
import com.capstone.interview.entity.Resume;
import com.capstone.interview.repository.InterviewParticipantRepository;
import com.capstone.interview.repository.InterviewRepository;
import com.capstone.interview.repository.MemberRepository;
import com.capstone.interview.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 이력서 업로드 및 관리 서비스.
 * PDF 파싱 → 텍스트 추출 → DB 저장을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final PdfParserService pdfParserService;
    private final ResumePreprocessorService resumePreprocessorService;
    private final ResumeRepository resumeRepository;
    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewParticipantRepository interviewParticipantRepository;

    /**
     * PDF 이력서를 업로드하고 파싱하여 DB에 저장한다.
     */
    @Transactional
    public ResumeResponse uploadPdfResume(String loginId, String title, MultipartFile file)
            throws IOException {

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        // PDF → 텍스트 변환
        String originalText = pdfParserService.extractText(file);

        if (originalText.isBlank()) {
            throw new IllegalArgumentException("PDF에서 텍스트를 추출할 수 없습니다.");
        }

        // DB 저장
        String keywords = preprocessSafely(originalText);
        Resume resume = Resume.builder()
                .member(member)
                .title(title)
                .originalText(originalText)
                .keywords(keywords)
                .build();

        Resume saved = resumeRepository.save(resume);
        log.info("[이력서 저장] id={}, memberId={}, 텍스트 길이={}",
                saved.getId(), member.getId(), originalText.length());

        return toResponse(saved);
    }

    /**
     * 텍스트로 직접 입력한 이력서를 DB에 저장한다.
     */
    @Transactional
    public ResumeResponse uploadTextResume(String loginId, ResumeUploadRequest request) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        if (request.originalText() == null || request.originalText().isBlank()) {
            throw new IllegalArgumentException("이력서 텍스트가 비어있습니다.");
        }

        String keywords = preprocessSafely(request.originalText());
        Resume resume = Resume.builder()
                .member(member)
                .title(request.title())
                .originalText(request.originalText())
                .keywords(keywords)
                .build();

        Resume saved = resumeRepository.save(resume);
        log.info("[이력서 저장] id={}, memberId={}", saved.getId(), member.getId());

        return toResponse(saved);
    }

    /**
     * 회원의 이력서 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<ResumeResponse> getResumesByLoginId(String loginId) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        return resumeRepository.findByMemberId(member.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 이력서 ID로 조회한다.
     */
    @Transactional(readOnly = true)
    public ResumeResponse getResume(Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("이력서를 찾을 수 없습니다: " + resumeId));
        return toResponse(resume);
    }

    /**
     * 이력서를 삭제한다. 본인의 이력서만 삭제 가능.
     * 면접 기록에서 참조 중인 경우 참조를 해제(NULL)한 뒤 삭제한다.
     */
    @Transactional
    public void deleteResume(String loginId, Long resumeId) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("이력서를 찾을 수 없습니다: " + resumeId));

        if (!resume.getMember().getId().equals(member.getId())) {
            throw new IllegalArgumentException("본인의 이력서만 삭제할 수 있습니다.");
        }

        // 면접 기록에서 이력서 참조 해제
        interviewRepository.findByResumeId(resumeId)
                .forEach(interview -> interview.clearResume());

        interviewParticipantRepository.findByResumeId(resumeId)
                .forEach(participant -> participant.clearResume());

        resumeRepository.delete(resume);
        log.info("[이력서 삭제] id={}, memberId={}", resumeId, member.getId());
    }

    private ResumeResponse toResponse(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getTitle(),
                resume.getOriginalText(),
                resume.getFileUrl(),
                resume.getKeywords(),
                resume.getCreatedAt()
        );
    }

    private String preprocessSafely(String originalText) {
        try {
            return resumePreprocessorService.preprocess(originalText);
        } catch (Exception e) {
            log.warn("[이력서 전처리 실패] 원문 저장은 계속 진행합니다. reason={}", e.getMessage());
            return null;
        }
    }
}
