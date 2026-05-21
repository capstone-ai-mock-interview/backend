package com.capstone.interview.repository;

import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    Optional<Interview> findBySessionId(String sessionId);
    //FeedbackService에서 "이 회원의 면접 목록"을 조회해야 하니까. JPA가 메서드 이름 읽고 SQL 자동 생성.
    List<Interview> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    //피드백 목록 조회 : 해당 회원의 COMPLETED 상태 면접만 최신순 조회
    List<Interview> findByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, InterviewStatus status);
    // 이력서 삭제 시 참조 해제용
}
