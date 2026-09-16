package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.entity.BalanceTransaction;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.repository.BalanceTransactionRepository;
import com.gxu.aihall.repository.UserRepository;
import com.gxu.aihall.util.UserPrivacyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 钱包服务：余额查询、充值、管理员调整。
 * 所有余额变更都在事务中通过悲观锁（SELECT ... FOR UPDATE）读取用户行，
 * 保证并发下不会出现丢失更新；每次变更都写入余额流水以便对账。
 */
@Service
public class WalletService {

    /** 单次充值上限（演示用，无真实支付） */
    private static final BigDecimal MAX_RECHARGE = new BigDecimal("10000");

    private final UserRepository userRepository;
    private final BalanceTransactionRepository transactionRepository;

    public WalletService(UserRepository userRepository,
                         BalanceTransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public BigDecimal getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        return UserPrivacyUtil.balanceOf(user);
    }

    public List<BalanceTransaction> listTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    /**
     * 学生自助充值（演示用）：仅累加余额并记录流水，不涉及真实支付。
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal recharge(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("充值金额必须大于 0");
        }
        if (amount.compareTo(MAX_RECHARGE) > 0) {
            throw new BizException("单次充值金额不能超过 " + MAX_RECHARGE.toPlainString() + " 元");
        }
        return applyChange(userId, amount, "RECHARGE", "自助充值", null);
    }

    /**
     * 管理员调整余额（可正可负）
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal adminAdjust(Long userId, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException("调整金额不能为 0");
        }
        String r = (remark == null || remark.isBlank()) ? "管理员调整余额" : remark.trim();
        return applyChange(userId, amount, "ADMIN_ADJUST", r, null);
    }

    /**
     * 统一的两用户转账（二手交易付款/收款）：
     * 先校验付款方余额，再扣款入账，并各记一条流水。
     * 多行加锁后，并发下的余额扣减不会出现超额或丢失更新。
     * @return 付款后的付款方余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal transfer(Long fromUserId, Long toUserId, BigDecimal amount, String orderNo, String remark) {
        if (fromUserId == null || toUserId == null) {
            throw new BizException("交易双方不能为空");
        }
        if (fromUserId.equals(toUserId)) {
            throw new BizException("不能与自己交易");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("交易金额必须大于 0");
        }

        // 固定按用户 id 升序加锁：避免两个用户互相交易时形成 AB-BA 死锁
        User payer = userRepository.findByIdForUpdate(fromUserId)
                .orElseThrow(() -> new BizException("付款方不存在"));
        User payee = userRepository.findByIdForUpdate(toUserId)
                .orElseThrow(() -> new BizException("收款方不存在"));

        BigDecimal payerBefore = UserPrivacyUtil.balanceOf(payer);
        if (payerBefore.compareTo(amount) < 0) {
            throw new BizException("余额不足，请先充值");
        }
        BigDecimal payerAfter = payerBefore.subtract(amount);
        BigDecimal payeeAfter = UserPrivacyUtil.balanceOf(payee).add(amount);

        LocalDateTime now = LocalDateTime.now();
        payer.setBalance(payerAfter);
        payer.setUpdateTime(now);
        payee.setBalance(payeeAfter);
        payee.setUpdateTime(now);
        userRepository.save(payer);
        userRepository.save(payee);

        transactionRepository.save(buildTx(fromUserId, amount.negate(), payerAfter, "PAY", remark, orderNo, now));
        transactionRepository.save(buildTx(toUserId, amount, payeeAfter, "INCOME", remark, orderNo, now));

        return payerAfter;
    }

    private BalanceTransaction buildTx(Long userId, BigDecimal delta, BigDecimal after,
                                       String type, String remark, String orderNo, LocalDateTime now) {
        BalanceTransaction tx = new BalanceTransaction();
        tx.setUserId(userId);
        tx.setAmount(delta);
        tx.setBalanceAfter(after);
        tx.setType(type);
        tx.setRemark(remark);
        tx.setRelatedOrderNo(orderNo);
        tx.setCreateTime(now);
        return tx;
    }

    /**
     * 统一的余额变更入口：悲观锁读用户 → 校验非负 → 写回 → 记流水。
     * 供充值、管理员调整等单用户场景复用。
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal applyChange(Long userId, BigDecimal delta, String type, String remark, String relatedOrderNo) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        BigDecimal before = UserPrivacyUtil.balanceOf(user);
        BigDecimal after = before.add(delta);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("余额不足");
        }
        user.setBalance(after);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);

        BalanceTransaction tx = new BalanceTransaction();
        tx.setUserId(userId);
        tx.setAmount(delta);
        tx.setBalanceAfter(after);
        tx.setType(type);
        tx.setRemark(remark);
        tx.setRelatedOrderNo(relatedOrderNo);
        tx.setCreateTime(LocalDateTime.now());
        transactionRepository.save(tx);

        return after;
    }
}
