package com.capstone.interview.repository;

import com.capstone.interview.entity.InterviewQna;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewQnARepository extends JpaRepository<InterviewQna, Long> {
    List<InterviewQna> findByInterviewId(Long interviewId);
}
