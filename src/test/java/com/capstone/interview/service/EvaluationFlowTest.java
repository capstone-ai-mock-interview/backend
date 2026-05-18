package com.capstone.interview.service;

import com.capstone.interview.config.MockLLMClient;
import com.capstone.interview.config.LLMClient;
import com.capstone.interview.dto.FeedbackListDto;
import com.capstone.interview.dto.FeedbackResponse;
import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewMode;
import com.capstone.interview.entity.InterviewQna;
import com.capstone.interview.entity.Member;
import com.capstone.interview.repository.InterviewParticipantRepository;
import com.capstone.interview.repository.InterviewQnaRepository;
import com.capstone.interview.repository.InterviewRepository;
import com.capstone.interview.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EvaluationFlowTest {

    private static EvaluationService evaluationService;
    private static FeedbackService feedbackService;
    private static InterviewRepository interviewRepository;
    private static InterviewQnaRepository interviewQnaRepository;
    private static InterviewParticipantRepository participantRepository;
    private static MemberRepository memberRepository;
    private static Interview testInterview;
    private static List<InterviewQna> testQnas;

    @BeforeAll
    static void setUp() throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        interviewRepository = mock(InterviewRepository.class);
        interviewQnaRepository = mock(InterviewQnaRepository.class);
        participantRepository = mock(InterviewParticipantRepository.class);
        memberRepository = mock(MemberRepository.class);
        LLMClient llmClient = new MockLLMClient();
        ObjectMapper objectMapper = new ObjectMapper();

        evaluationService = new EvaluationService(
                interviewRepository, interviewQnaRepository, participantRepository, llmClient, objectMapper);
        feedbackService = new FeedbackService(
                interviewRepository, interviewQnaRepository, participantRepository, memberRepository, objectMapper);

        Member member = createMember(1L, "test1", "Tester");
        testInterview = createInterview(1L, "sess-test-0001", member, "BACKEND");

        testQnas = new ArrayList<>();
        testQnas.add(createQna(1L, testInterview, 1,
                "What is Dependency Injection in Spring Boot?",
                "It is a pattern where objects are injected externally instead of being created directly.", false));
        testQnas.add(createQna(2L, testInterview, 2,
                "What is the difference between constructor injection and field injection?",
                "Constructor injection guarantees immutability and is easier to test.", true));
        testQnas.add(createQna(3L, testInterview, 3,
                "How do you solve the JPA N+1 problem?",
                "Use fetch join or @EntityGraph to load related entities in one query.", false));

        when(interviewRepository.findBySessionId("sess-test-0001"))
                .thenReturn(Optional.of(testInterview));
        when(interviewQnaRepository.findByInterviewOrderBySequenceNumberAsc(testInterview))
                .thenReturn(testQnas);
        for (InterviewQna qna : testQnas) {
            when(interviewQnaRepository.findByInterviewAndSequenceNumber(testInterview, qna.getSequenceNumber()))
                    .thenReturn(Optional.of(qna));
        }
        when(interviewQnaRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(interviewRepository.save(any(Interview.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(interviewRepository.findByMemberIdAndStatusOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(List.of(testInterview));
        when(memberRepository.findByLoginId("test1"))
                .thenReturn(Optional.of(member));
    }

    @Test
    @Order(1)
    @DisplayName("Stage 1: EvaluationService.evaluate() - MockLLM parse and save")
    void stage1_evaluate() {
        System.out.println("\n=== Stage 1: EvaluationService.evaluate() ===");
        System.out.println("[Input] Dummy Q&A pairs:");
        for (InterviewQna qna : testQnas) {
            System.out.printf("  Q%d: %s%n", qna.getSequenceNumber(), qna.getQuestionContent());
            System.out.printf("  A%d: %s%n%n", qna.getSequenceNumber(), qna.getAnswerContent());
        }

        evaluationService.evaluate("sess-test-0001");

        System.out.println("[Output] After evaluate():");
        for (InterviewQna qna : testQnas) {
            System.out.printf("  Q%d modelAnswer: %s%n", qna.getSequenceNumber(),
                    qna.getModelAnswer() != null ? qna.getModelAnswer() : "(none)");
            System.out.printf("  Q%d individualFeedback: %s%n%n", qna.getSequenceNumber(),
                    qna.getIndividualFeedback() != null ? qna.getIndividualFeedback() : "(none)");
        }
        System.out.println("  totalFeedback (raw): " + testInterview.getTotalFeedback());

        assertNotNull(testQnas.get(0).getModelAnswer());
        assertNotNull(testQnas.get(0).getIndividualFeedback());
        assertNotNull(testInterview.getTotalFeedback());
        assertTrue(testInterview.getTotalFeedback().contains("[SCORE]"));
        assertTrue(testInterview.getTotalFeedback().contains("[CHART]"));

        System.out.println("\n  >> PASSED: MockLLM response parsed and saved correctly.");
    }

    @Test
    @Order(2)
    @DisplayName("Stage 2: FeedbackService.getFeedback() - Read and format for frontend")
    void stage2_getFeedback() {
        if (testInterview.getTotalFeedback() == null) {
            evaluationService.evaluate("sess-test-0001");
        }

        System.out.println("\n=== Stage 2: FeedbackService.getFeedback() ===");

        FeedbackResponse response = feedbackService.getFeedback("sess-test-0001", "test1");

        System.out.println("[Output] FeedbackResponse:");
        System.out.println("  success: " + response.isSuccess());
        System.out.println("  totalFeedback: " + response.getTotalFeedback());
        System.out.println("  competencyChart: " + response.getCompetencyChart());
        System.out.println("  qaPairs count: " + response.getQaPairs().size());
        response.getQaPairs().forEach(qp -> {
            System.out.printf("    Q%d: %s%n", qp.sequenceNumber(), qp.questionContent());
            System.out.printf("      modelAnswer: %s%n", qp.modelAnswer() != null ? qp.modelAnswer() : "(none)");
            System.out.printf("      individualFeedback: %s%n%n", qp.individualFeedback() != null ? qp.individualFeedback() : "(none)");
        });

        assertTrue(response.isSuccess());
        assertNotNull(response.getTotalFeedback());
        assertFalse(response.getTotalFeedback().contains("[SCORE]"), "SCORE tag should be stripped");
        assertNotNull(response.getCompetencyChart());
        assertEquals(3, response.getQaPairs().size());

        System.out.println("\n  >> PASSED: Feedback formatted correctly for frontend.");
    }

    @Test
    @Order(3)
    @DisplayName("Stage 3: FeedbackService.getFeedbackList() - List with overallScore")
    void stage3_getFeedbackList() {
        if (testInterview.getTotalFeedback() == null) {
            evaluationService.evaluate("sess-test-0001");
        }

        System.out.println("\n=== Stage 3: FeedbackService.getFeedbackList() ===");

        List<FeedbackListDto> list = feedbackService.getFeedbackList("test1");

        FeedbackListDto item = list.get(0);
        System.out.println("[Output] FeedbackListDto:");
        System.out.println("  sessionId: " + item.sessionId());
        System.out.println("  category: " + item.category());
        System.out.println("  overallScore: " + item.overallScore());
        System.out.println("  createdAt: " + item.createdAt());

        assertFalse(list.isEmpty());
        assertEquals("sess-test-0001", item.sessionId());
        assertEquals("BACKEND", item.category());
        assertNotNull(item.overallScore());

        System.out.println("\n  >> PASSED: FeedbackList with overallScore parsed correctly.");
    }

    private static Member createMember(Long id, String loginId, String name) throws Exception {
        Member member = Member.builder().loginId(loginId).password("encoded").name(name).build();
        setField(member, "id", id);
        setField(member, "createdAt", LocalDateTime.now());
        setField(member, "updatedAt", LocalDateTime.now());
        return member;
    }

    private static Interview createInterview(Long id, String sessionId, Member member, String category) throws Exception {
        Interview interview = Interview.builder()
                .member(member)
                .category(category)
                .sessionId(sessionId)
                .maxParticipants(1)
                .mode(InterviewMode.SOLO)
                .build();
        interview.start();
        setField(interview, "id", id);
        setField(interview, "createdAt", LocalDateTime.now());
        setField(interview, "updatedAt", LocalDateTime.now());
        return interview;
    }

    private static InterviewQna createQna(Long id, Interview interview, int seq, String question, String answer, boolean followUp) throws Exception {
        InterviewQna qna = new InterviewQna();
        setField(qna, "id", id);
        setField(qna, "interview", interview);
        setField(qna, "sequenceNumber", seq);
        setField(qna, "questionContent", question);
        setField(qna, "answerContent", answer);
        setField(qna, "answerSummary", "[\"" + answer.replace("\"", "\\\"") + "\"]");
        setField(qna, "isFollowUp", followUp);
        setField(qna, "createdAt", LocalDateTime.now());
        setField(qna, "updatedAt", LocalDateTime.now());
        return qna;
    }

    private static void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = findField(obj.getClass(), fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
