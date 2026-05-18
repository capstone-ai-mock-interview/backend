package com.capstone.interview.repository;

import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewQna;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InterviewQnaRepository extends JpaRepository<InterviewQna, Long> {
    // 면접의 모든 질문-답변을 순서대로 조회
    List<InterviewQna> findByInterviewOrderBySequenceNumberAsc(Interview interview);

    List<InterviewQna> findByInterviewAndRespondentMemberIdOrderBySequenceNumberAsc(
            Interview interview, Long respondentMemberId);

    Optional<InterviewQna> findByInterviewAndSequenceNumber(Interview interview, Integer sequenceNumber);
    int countByInterview(Interview interview);
    // 면접 기록 삭제 시 관련 QnA 일괄 삭제 (셀프 참조 FK 고려하여 네이티브 쿼리 사용)
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM InterviewQna q WHERE q.interview = :interview")
    void deleteAllByInterview(@org.springframework.data.repository.query.Param("interview") Interview interview);
}
