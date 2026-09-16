package com.gxu.aihall.service;

import com.gxu.aihall.common.PageQuery;
import com.gxu.aihall.common.PageResult;
import com.gxu.aihall.entity.AuditLog;
import com.gxu.aihall.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志写入与查询。
 * <p><b>写入必须独立事务（REQUIRES_NEW）</b>：业务方法失败回滚时，「有人尝试过这个操作」
 * 这条信息本身要留下。也正因为如此，写入失败绝不能影响主流程 —— 一律吞掉只告警。
 */
@Slf4j
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 写一条审计记录。任何异常都只记录到业务日志，绝不向外抛 ——
     * 审计是旁观者，不能因为它炸了就让管理员改不了余额。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(AuditLog entry) {
        try {
            if (entry == null) return;
            if (entry.getCreateTime() == null) entry.setCreateTime(LocalDateTime.now());
            if (entry.getDetail() != null && entry.getDetail().length() > AuditLog.MAX_DETAIL) {
                entry.setDetail(entry.getDetail().substring(0, AuditLog.MAX_DETAIL) + "…(已截断)");
            }
            if (entry.getErrorMsg() != null && entry.getErrorMsg().length() > 500) {
                entry.setErrorMsg(entry.getErrorMsg().substring(0, 500));
            }
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败（不影响业务操作）: {}", e.getMessage());
        }
    }

    /**
     * 管理端查询：按操作人 / 操作类型 / 对象 / 结果 / 时间范围过滤，数据库分页。
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> query(Long operatorId, String action, String targetType,
                                                 Long targetId, String result, LocalDateTime from,
                                                 LocalDateTime to, int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (operatorId != null) ps.add(cb.equal(root.get("operatorId"), operatorId));
            if (StringUtils.hasText(action)) ps.add(cb.equal(root.get("action"), action.trim()));
            if (StringUtils.hasText(targetType)) ps.add(cb.equal(root.get("targetType"), targetType.trim()));
            if (targetId != null) ps.add(cb.equal(root.get("targetId"), targetId));
            if (StringUtils.hasText(result)) ps.add(cb.equal(root.get("result"), result.trim()));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createTime"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("createTime"), to));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<AuditLog> paged = auditLogRepository.findAll(
                spec, q.pageable(Sort.by(Sort.Direction.DESC, "createTime")));
        List<Map<String, Object>> rows = new ArrayList<>(paged.getNumberOfElements());
        for (AuditLog row : paged.getContent()) {
            rows.add(toMap(row));
        }
        return PageResult.fromPage(paged, rows);
    }

    /**
     * 对外结构。<b>不含任何敏感字段</b> —— detail 本身已经是白名单字段拼出来的。
     */
    public Map<String, Object> toMap(AuditLog row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("operatorId", row.getOperatorId());
        m.put("operatorName", row.getOperatorName());
        m.put("action", row.getAction());
        m.put("targetType", row.getTargetType());
        m.put("targetId", row.getTargetId());
        m.put("detail", row.getDetail());
        m.put("result", row.getResult());
        m.put("errorMsg", row.getErrorMsg());
        m.put("ip", row.getIp());
        m.put("createTime", row.getCreateTime() == null ? null : row.getCreateTime().toString());
        return m;
    }
}
