package com.capstone.interview.repository;

import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewParticipant;
import com.capstone.interview.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewParticipantRepository extends JpaRepository<InterviewParticipant, Long> {

    List<InterviewParticipant> findByInterviewOrderByJoinedAtAsc(Interview interview);

    Optional<InterviewParticipant> findByInterviewAndMember(Interview interview, Member member);

    long countByInterview(Interview interview);

    long countByInterviewAndReadyTrue(Interview interview);

    List<InterviewParticipant> findByMemberIdOrderByJoinedAtDesc(Long memberId);
}
