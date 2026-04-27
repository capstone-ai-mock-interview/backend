package com.capstone.interview.repository;

import com.capstone.interview.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    Optional<Interview> findBySessionId(String sessionId);
}
