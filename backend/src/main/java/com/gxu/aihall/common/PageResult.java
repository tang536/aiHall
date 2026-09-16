package com.gxu.aihall.common;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果（与前端约定的 {list,total,page,size} 结构一致）。
 * 由服务层用数据库分页查询 {@link Page} 转换而来，因此只会物化当前页的数据。
 */
@Data
public class PageResult<T> {
    private List<T> list = new ArrayList<>();
    private long total;
    private int page;
    private int size;

    /** 直接由 Spring Data 的分页结果转换（页码 0 基 → 1 基） */
    public static <T> PageResult<T> from(Page<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setList(page.getContent());
        r.setTotal(page.getTotalElements());
        r.setPage(page.getNumber() + 1);
        r.setSize(page.getSize());
        return r;
    }

    /** 同 {@link #from(Page)}，顺带做实体 → VO 的映射 */
    public static <E, T> PageResult<T> from(Page<E> page, Function<E, T> mapper) {
        PageResult<T> r = new PageResult<>();
        List<T> mapped = new ArrayList<>(page.getNumberOfElements());
        for (E e : page.getContent()) {
            mapped.add(mapper.apply(e));
        }
        r.setList(mapped);
        r.setTotal(page.getTotalElements());
        r.setPage(page.getNumber() + 1);
        r.setSize(page.getSize());
        return r;
    }

    /**
     * 由「已批量转换好的当前页数据」构造结果。
     * 推荐用它而不是 {@link #from(Page, Function)}：VO 转换往往要补查关联用户，
     * 批量转换可以一次性把这一页的关联数据查完，避免逐行查询（N+1）。
     */
    public static <E, T> PageResult<T> fromPage(Page<E> page, List<T> mappedCurrentPage) {
        PageResult<T> r = new PageResult<>();
        r.setList(mappedCurrentPage == null ? new ArrayList<>() : mappedCurrentPage);
        r.setTotal(page.getTotalElements());
        r.setPage(page.getNumber() + 1);
        r.setSize(page.getSize());
        return r;
    }

    /**
     * 内存分页：调用方已经把完整列表查出来了，这里只做切片。
     * @deprecated 会把整表读进内存，数据量一大就出问题。新代码请用 {@code PageQuery} +
     *             {@code JpaSpecificationExecutor} / {@code Pageable} 做数据库分页，
     *             再用 {@link #from(Page)} 转换。
     */
    @Deprecated
    public static <T> PageResult<T> of(List<T> all, int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        PageResult<T> r = new PageResult<>();
        int total = all == null ? 0 : all.size();
        r.setTotal(total);
        r.setPage(q.page());
        r.setSize(q.size());
        if (total > 0) {
            int from = Math.min((q.page() - 1) * q.size(), total);
            int to = Math.min(from + q.size(), total);
            r.setList(all.subList(from, to));
        }
        return r;
    }
}
