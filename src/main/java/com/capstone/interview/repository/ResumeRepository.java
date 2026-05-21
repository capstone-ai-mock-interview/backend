package com.capstone.interview.repository;

import com.capstone.interview.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByMemberIdAndDeletedAtIsNull(Long memberId);

    Optional<Resume> findByIdAndDeletedAtIsNull(Long id);
}
