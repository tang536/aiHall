package com.gxu.aihall.service;

import com.gxu.aihall.entity.SystemSetting;
import com.gxu.aihall.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

/**
 * 系统设置服务，处理键值对形式的系统配置
 */
@Service
public class SystemSettingService {

    private static final String FOCUS_PASSWORD_KEY = "focus_mode_password";

    private final SystemSettingRepository repository;

    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取专注模式密码
     */
    public String getFocusPassword() {
        return repository.findBySettingKey(FOCUS_PASSWORD_KEY)
                .map(SystemSetting::getSettingValue)
                .orElse(null);
    }

    /**
     * 设置专注模式密码
     */
    public void setFocusPassword(String password) {
        SystemSetting setting = repository.findBySettingKey(FOCUS_PASSWORD_KEY)
                .orElse(new SystemSetting());
        if (setting.getId() == null) {
            setting.setSettingKey(FOCUS_PASSWORD_KEY);
        }
        setting.setSettingValue(password);
        repository.save(setting);
    }

    /**
     * 验证专注模式密码
     */
    public boolean verifyFocusPassword(String input) {
        String saved = getFocusPassword();
        return saved != null && saved.equals(input);
    }

    /**
     * 检查是否已设置专注模式密码
     */
    public boolean hasFocusPassword() {
        return getFocusPassword() != null;
    }
}
