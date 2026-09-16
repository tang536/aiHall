package com.gxu.aihall.repository;

import com.gxu.aihall.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /** 按平台账号（学号）或姓名模糊搜索用户 */
    List<User> findByUsernameContainingOrRealNameContainingOrderByIdAsc(String username, String realName);

    /**
     * 悲观锁读取用户（SELECT ... FOR UPDATE）。
     * 用于余额变动场景，避免并发下出现超额扣款/丢失更新。必须在事务中调用。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
