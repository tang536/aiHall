package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByStudentId(Long studentId);
    List<Course> findByStudentIdIsNull();
    List<Course> findByStudentIdOrStudentIdIsNull(Long studentId);
    void deleteByStudentId(Long studentId);
}
