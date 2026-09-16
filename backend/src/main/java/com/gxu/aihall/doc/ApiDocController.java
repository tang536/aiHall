package com.gxu.aihall.doc;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 接口文档端点。
 * <p>做成<b>仅管理员可见</b>：完整的接口清单（含管理端路径、参数名）对外就是一张攻击面地图。
 * 自行搭建的团队要看文档，用管理员令牌访问即可。
 * <p>人类可读的浏览页在 {@code /api-docs.html}（纯静态，零第三方资源，离线也能开），
 * 页面里填令牌后调本接口拉数据。
 */
@RestController
public class ApiDocController {

    private final OpenApiDocService docService;
    private final AuthService authService;

    public ApiDocController(OpenApiDocService docService, AuthService authService) {
        this.docService = docService;
        this.authService = authService;
    }

    /** OpenAPI 3.0 文档（JSON） */
    @GetMapping(value = "/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openApi(@RequestHeader(value = "Authorization", required = false) String token) {
        requireAdmin(token);
        return docService.build("/api");
    }

    /** 接口规模概览：给浏览页做首屏统计用 */
    @GetMapping(value = "/api-docs/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> stats(@RequestHeader(value = "Authorization", required = false) String token) {
        requireAdmin(token);
        return docService.summaryStats();
    }

    private void requireAdmin(String token) {
        User user = authService.requireLogin(token, "未登录");
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BizException(403, "无权查看接口文档");
        }
    }
}
