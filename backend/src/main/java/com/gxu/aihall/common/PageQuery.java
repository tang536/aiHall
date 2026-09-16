package com.gxu.aihall.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页入参的统一归一化。
 * 对外接口的 {@code page} 从 1 开始（与前端约定一致），而 Spring Data 的
 * {@link Pageable} 从 0 开始，且必须挡住超大的 {@code size}（否则一次请求就能把整表拉进内存）。
 * 这两个转换散落在每个 Controller 里很容易漏，统一收敛到这里。
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** 归一化外部入参：page 至少为 1，size 落在 [1, MAX_SIZE] */
    public static PageQuery of(Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageQuery(p, s);
    }

    public Pageable pageable(Sort sort) {
        return PageRequest.of(page - 1, size, sort);
    }

    public Pageable pageable() {
        return PageRequest.of(page - 1, size);
    }
}
