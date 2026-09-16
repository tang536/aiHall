package com.gxu.aihall.doc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 从 Spring MVC 的映射表生成 OpenAPI 3.0 文档。
 * <p><b>为什么自己写而不是引 springdoc</b>：本机构建环境的 Maven 仓库访问不到外网镜像
 * （新依赖一律 404），springdoc 装不上。与其手写一份必然过期的接口清单，
 * 不如从 {@link RequestMappingHandlerMapping} 里把 Spring 已经解析好的映射直接导出来 ——
 * 这份文档<b>永远不会和代码脱节</b>，因为源头就是运行时真实的映射表。
 * <p>拿不到的东西要如实说明：Javadoc 在编译期就丢了，运行时读不到，
 * 所以接口说明要么来自 {@link ApiDoc}，要么退回方法名。字段级 schema 也不做推导，
 * 请求体统一标注为 object。
 */
@Slf4j
@Service
public class OpenApiDocService {

    private final RequestMappingHandlerMapping handlerMapping;

    // actuator 也会注册一个 RequestMappingHandlerMapping 子类（controllerEndpointHandlerMapping），
    // 必须用 bean 名称精确限定，否则构造函数注入会因「找到 2 个候选 bean」而启动失败。
    public OpenApiDocService(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    /** 生成 OpenAPI 3.0 文档（JSON 结构，由 Jackson 序列化） */
    public Map<String, Object> build(String serverUrl) {
        Map<String, Object> paths = new TreeMap<>();
        Map<String, Map<String, Object>> tagMap = new TreeMap<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Method method = handler.getMethod();

            // 跳过 OpenAPI 之外的框架端点（错误页、静态资源等）
            Set<String> patterns = patternsOf(info);
            if (patterns.isEmpty()) continue;
            Set<RequestMethod> verbs = info.getMethodsCondition().getMethods();
            Collection<RequestMethod> effectiveVerbs = verbs.isEmpty() ? EnumSet.allOf(RequestMethod.class) : verbs;

            String tag = tagOf(handler.getBeanType(), method);
            tagMap.computeIfAbsent(tag, k -> {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", k);
                return t;
            });
            String summary = summaryOf(method);

            for (String pattern : patterns) {
                if (pattern.contains("{path}") || pattern.startsWith("/error")) continue;
                Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(pattern, k -> new TreeMap<>());
                for (RequestMethod verb : effectiveVerbs) {
                    pathItem.put(verb.name().toLowerCase(), operation(pattern, verb, method, handler.getBeanType(),
                            tag, summary));
                }
            }
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("openapi", "3.0.1");
        doc.put("info", Map.of(
                "title", "AI学生事务智能办事大厅 · 接口文档",
                "version", "1.0.0",
                "description", "由运行时映射表自动生成，与代码同步；接口说明来自 @ApiDoc 注解，未标注的显示方法名。"));
        doc.put("servers", List.of(Map.of("url", serverUrl)));
        doc.put("tags", new ArrayList<>(tagMap.values()));
        doc.put("paths", paths);
        doc.put("components", Map.of("securitySchemes", Map.of(
                "bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT"))));
        return doc;
    }

    private Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() == null) return Set.of();
        return info.getPathPatternsCondition().getPatternValues();
    }

    private String tagOf(Class<?> beanType, Method method) {
        ApiDoc onMethod = method.getAnnotation(ApiDoc.class);
        if (onMethod != null && !onMethod.tag().isEmpty()) return onMethod.tag();
        ApiDoc onType = beanType.getAnnotation(ApiDoc.class);
        if (onType != null && !onType.tag().isEmpty()) return onType.tag();
        String simple = beanType.getSimpleName();
        return simple.endsWith("Controller") ? simple.substring(0, simple.length() - "Controller".length()) : simple;
    }

    private String summaryOf(Method method) {
        ApiDoc doc = method.getAnnotation(ApiDoc.class);
        return doc != null ? doc.value() : method.getName();
    }

    private boolean requiresAdmin(String pattern) {
        return pattern.startsWith("/api/admin/");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(String pattern, RequestMethod verb, Method method,
                                          Class<?> beanType, String tag, String summary) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(tag));
        op.put("summary", summary);
        op.put("operationId", beanType.getSimpleName() + "." + method.getName() +
                (verb == RequestMethod.GET ? "" : "_" + verb.name().toLowerCase()));

        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> requestBody = null;
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> type = p.getType();
            if (type.getName().startsWith("jakarta.servlet") || type.getName().startsWith("javax.servlet")) continue;

            String name = p.getName();
            if (p.isAnnotationPresent(PathVariable.class)) {
                parameters.add(param(name, "path", type, true, null));
            } else if (p.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = p.getAnnotation(RequestParam.class);
                String alias = rp.name().isEmpty() ? rp.value() : rp.name();
                String pname = alias.isEmpty() ? name : alias;
                boolean required = rp.required() && rp.defaultValue().isEmpty();
                parameters.add(param(pname, "query", type, required, rp.defaultValue().isEmpty() ? null : rp.defaultValue()));
            } else if (p.isAnnotationPresent(RequestHeader.class)) {
                RequestHeader rh = p.getAnnotation(RequestHeader.class);
                String alias = rh.name().isEmpty() ? rh.value() : rh.name();
                parameters.add(param(alias.isEmpty() ? name : alias, "header", type, rh.required(),
                        rh.defaultValue().isEmpty() ? null : rh.defaultValue()));
            } else if (p.isAnnotationPresent(RequestBody.class)) {
                requestBody = Map.of(
                        "required", p.getAnnotation(RequestBody.class).required(),
                        "content", Map.of("application/json", Map.of("schema", schemaOf(type))));
            }
            // 其余（HttpServletRequest 之外的普通入参）在本工程里不存在，忽略即可
        }

        // 登录要求：/api/admin/** 强制 ADMIN；其余看方法签名里有没有接 Authorization 请求头。
        // 刻意不给「不需要登录」的接口挂 security —— 文档写错了比不写更误导。
        boolean admin = requiresAdmin(pattern);
        if (admin || acceptsToken(method)) {
            op.put("security", List.of(Map.of("bearerAuth", List.of())));
        }
        if (admin) {
            op.put("description", "需要管理员令牌（/api/admin/** 由 AuthInterceptor 强制 ADMIN）");
        } else if (acceptsToken(method)) {
            op.put("description", "需要登录令牌");
        }

        if (!parameters.isEmpty()) op.put("parameters", parameters);
        if (requestBody != null) op.put("requestBody", requestBody);

        // 统一响应结构 {code, message, data}：data 的类型按方法返回类型推导
        Map<String, Object> bodySchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "integer", "example", 200),
                        "message", Map.of("type", "string"),
                        "data", schemaOf(method.getReturnType())));
        Map<String, Object> mediaType = Map.of("application/json", Map.of("schema", bodySchema));
        Map<String, Object> response = Map.of(
                "description", "统一响应结构 {code,message,data}，code=200 表示成功",
                "content", mediaType);
        op.put("responses", Map.of("200", response));
        return op;
    }

    /** 方法签名里带 Authorization 请求头的，说明需要登录 */
    private boolean acceptsToken(Method method) {
        for (Parameter p : method.getParameters()) {
            if (p.isAnnotationPresent(RequestHeader.class)
                    && "authorization".equalsIgnoreCase(headerName(p))) {
                return true;
            }
        }
        return false;
    }

    private String headerName(Parameter p) {
        RequestHeader rh = p.getAnnotation(RequestHeader.class);
        String alias = rh.name().isEmpty() ? rh.value() : rh.name();
        return alias.isEmpty() ? p.getName() : alias;
    }

    private Map<String, Object> param(String name, String in, Class<?> type, boolean required, String defaultValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("in", in);
        m.put("required", required);
        if (defaultValue != null) m.put("example", defaultValue);
        m.put("schema", schemaOf(type));
        return m;
    }

    private Map<String, Object> schemaOf(Class<?> type) {
        if (type == null) return Map.of("type", "object");
        if (type == void.class || type == Void.class) return Map.of("type", "object");
        if (type == String.class || CharSequence.class.isAssignableFrom(type)) return Map.of("type", "string");
        if (type == boolean.class || type == Boolean.class) return Map.of("type", "boolean");
        if (type == long.class || type == Long.class || type == int.class || type == Integer.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            return Map.of("type", "integer");
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class
                || Number.class.isAssignableFrom(type)) {
            return Map.of("type", "number");
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            Class<?> component = type.isArray() ? type.getComponentType() : Object.class;
            return Map.of("type", "array", "items", schemaOf(component));
        }
        if (Map.class.isAssignableFrom(type)) return Map.of("type", "object");
        if (type == LocalDateTime.class) return Map.of("type", "string", "format", "date-time");
        if (type == LocalDate.class) return Map.of("type", "string", "format", "date");
        // 自定义 VO / 实体：不做字段级推导，避免把隐私字段（password/phone）写进文档
        return Map.of("type", "object");
    }

    /** 供 HTML 页面展示的接口统计 */
    public Map<String, Object> summaryStats() {
        Map<String, Object> doc = build("/api");
        int ops = 0;
        for (Object v : ((Map<String, Object>) doc.get("paths")).values()) {
            ops += ((Map<String, Object>) v).size();
        }
        return Map.of("paths", ((Map<String, Object>) doc.get("paths")).size(),
                "operations", ops,
                "tags", ((List<?>) doc.get("tags")).size());
    }
}
