package com.gxu.aihall.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将业务异常统一转换为 Result 结构返回，
 * 消除各 Controller 内重复的 try-catch / 私有异常处理样板。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按携带的错误码返回 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 悲观锁获取失败 / 死锁：多发生在二手交易的并发下单场景，
     * 返回可读提示而不是把数据库异常抛给前端。
     */
    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class})
    public Result<Void> handleLockFailure(Exception e) {
        log.warn("并发锁冲突: {}", e.getMessage());
        return Result.error(409, "当前操作过于频繁，请稍后重试");
    }

    /** 其余运行时异常：返回错误消息（500） */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        // 必须留痕：未预期的异常此前被吞成一句 500 提示，线上无从排查。
        // 消息截断到 500 字：框架异常有时会把整个请求体塞进 message，既不安全也刷屏日志。
        log.error("未捕获的运行时异常: {}", truncate(e.getMessage()), e);
        // 只把「本工程自己抛的」异常消息回给前端 —— 那些文案是刻意写给用户看的
        // （如「账号或密码错误，绑定失败」）。框架/驱动抛出的消息可能带 SQL、文件路径、
        // 甚至请求体片段，回给前端属于信息泄露，统一换成通用文案，细节留在服务端日志里。
        if (isOwnMessage(e)) {
            return Result.error(e.getMessage() == null ? "操作失败" : e.getMessage());
        }
        return Result.error("服务处理异常，请稍后重试");
    }

    /** 异常的抛出点是否在本工程内（栈顶类包名判断） */
    private boolean isOwnMessage(Throwable e) {
        StackTraceElement[] trace = e.getStackTrace();
        return trace.length > 0 && trace[0].getClassName().startsWith("com.gxu.aihall");
    }

    private String truncate(String raw) {
        if (raw == null) return "";
        return raw.length() <= 500 ? raw : raw.substring(0, 500) + "…(已截断)";
    }
}
