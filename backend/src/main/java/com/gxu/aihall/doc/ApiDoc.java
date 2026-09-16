package com.gxu.aihall.doc;

import java.lang.annotation.*;

/**
 * 给接口补一句人话说明（可选）。
 * <p>自动生成的接口文档只能拿到方法签名，拿不到 Javadoc —— 注释在编译期就丢了，
 * 运行时读不到。所以需要给外部对接方看的接口补上 {@code summary}/{@code description}，
 * 其余的按方法名兜底，不强制逐个标注。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiDoc {

    /** 一句话说明这个接口做什么 */
    String value();

    /** 分组名（默认取 Controller 类名去掉 Controller 后缀） */
    String tag() default "";

    /** 是否需要登录（默认按路径推断：/api/admin/** 需要 ADMIN，其余不标） */
    String auth() default "";
}
