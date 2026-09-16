package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.doc.ApiDoc;
import com.gxu.aihall.entity.BalanceTransaction;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.WalletService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钱包（余额）接口：查询余额、自助充值、余额流水。
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final AuthService authService;
    private final WalletService walletService;

    public WalletController(AuthService authService, WalletService walletService) {
        this.authService = authService;
        this.walletService = walletService;
    }

    @GetMapping
    public Result<Map<String, Object>> wallet(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        Map<String, Object> data = new HashMap<>();
        data.put("balance", walletService.getBalance(user.getId()));
        return Result.success(data);
    }

    /** 自助充值（演示用，无真实支付）：金额变更与流水写入在同一事务内完成 */
    @ApiDoc("余额充值（演示用途：自助即时到账，未接真实支付）")
    @PostMapping("/recharge")
    public Result<Map<String, Object>> recharge(@RequestHeader(value = "Authorization", required = false) String token,
                                                @RequestBody Map<String, Object> body) {
        User user = authService.requireLogin(token, "未登录");
        BigDecimal amount = parseAmount(body.get("amount"));
        BigDecimal balance = walletService.recharge(user.getId(), amount);
        return Result.success("充值成功", Map.of("balance", balance));
    }

    @GetMapping("/transactions")
    public Result<List<BalanceTransaction>> transactions(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(walletService.listTransactions(user.getId()));
    }

    private BigDecimal parseAmount(Object raw) {
        if (raw == null) return null;
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new com.gxu.aihall.common.BizException("金额格式不正确");
        }
    }
}
