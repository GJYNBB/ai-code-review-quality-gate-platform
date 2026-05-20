package com.acrqg.platform.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建项目请求体（design.md §8.4）。
 *
 * <p>对应 {@code POST /api/v1/projects}。Bean Validation 在
 * {@code ProjectController} 上通过 {@code @Valid} 触发；不通过时由
 * {@link com.acrqg.platform.common.exception.GlobalExceptionHandler} 映射为
 * {@code VALIDATION_ERROR} + 400（R4.3）。
 *
 * <p>{@code language} 通过 {@link Pattern} 限制为 design 列出的 5 种取值，
 * 与 design.md §8.4 一致；其它语言的扩展应在 {@code system_param} 中维护
 * 而非硬编码进 DTO。
 *
 * @param name           项目名称（非空，1..128）
 * @param description    项目描述（可空，最长 512）
 * @param defaultBranch  默认分支（非空，1..128）
 * @param language       主要语言：Java / Python / JavaScript / TypeScript / Go
 */
public record ProjectCreateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotBlank @Size(max = 128) String defaultBranch,
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "Java|Python|JavaScript|TypeScript|Go",
                 message = "language 必须为 Java / Python / JavaScript / TypeScript / Go 之一")
        String language
) {
}
