package com.gxu.aihall.repository;

import com.gxu.aihall.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

/** 审计日志查询：管理端按操作人 / 操作类型 / 时间范围查（配合 {@code JpaSpecificationExecutor} 做分页） */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    /**
     * 清理指定时间之前的记录（归档/合规保留策略用）。
     * 实际是否需要配定时任务取决于合规要求，这里只提供删除能力。
     */
    long deleteByCreateTimeBefore(LocalDateTime deadline);

    /** 某对象最近的操作记录（变更历史视图用，按时间倒序） */
    List<AuditLog> findTop20ByTargetTypeAndTargetIdOrderByCreateTimeDesc(String targetType, Long targetId);
}
