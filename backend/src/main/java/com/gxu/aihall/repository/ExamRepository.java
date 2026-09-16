package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByStudentIdOrderByExamTimeDesc(Long studentId);
    List<Exam> findByStudentIdIsNullOrderByExamTimeDesc();
    List<Exam> findByStudentIdOrStudentIdIsNullOrderByExamTimeDesc(Long studentId);
    void deleteByStudentId(Long studentId);
}
