package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application> findByApplicationNo(String applicationNo);
    List<Application> findByStudentIdOrderByCreateTimeDesc(Long studentId);
    List<Application> findByTypeOrderByCreateTimeDesc(String type);
    List<Application> findByStatusOrderByCreateTimeDesc(String status);
    List<Application> findAllByOrderByCreateTimeDesc();
    long countByStatus(String status);
    long countByType(String type);
    List<Application> findByCreateTimeAfter(LocalDateTime time);
}
