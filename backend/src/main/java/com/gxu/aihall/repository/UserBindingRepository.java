package com.gxu.aihall.repository;

import com.gxu.aihall.entity.UserBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBindingRepository extends JpaRepository<UserBinding, Long> {
    List<UserBinding> findByUserId(Long userId);
    Optional<UserBinding> findByUserIdAndPlatform(Long userId, String platform);

    /** 按「平台 + 绑定账号」反查绑定记录：用于学校账号登录（校验绑定关系） */
    Optional<UserBinding> findFirstByPlatformAndBindAccountAndStatus(String platform, String bindAccount, Integer status);

    /**
     * 列出某教务账号的全部绑定记录。
     * 用于「一个教务账号只能被一个平台账号活跃绑定」的校验——否则同一学号可能登进别人的账号。
     */
    List<UserBinding> findByPlatformAndBindAccount(String platform, String bindAccount);
}
