package com.acrqg.platform.project.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新项目请求体。
 *
 * <p>对应 {@code PUT /api/v1/projects/{id}}。所有字段均为可选（PATCH 风格），
 * Service 层只更新非 {@code null} 字段；这样能避免误把已有字段被覆盖为 {@code null}。
 *
 * <p>{@code name} 不允许在更新时修改：项目名称变更影响审计与 webhook 路径回写，
 * 安全起见暂不开放修改入口（design.md §6.2 中的 {@code update} 仅描述更新接口
 * 行为，未列名称变更）。如确需重命名，未来可单独提供 admin 接口。
 *
 * <p>Covers: R4.3, R4.4。
 *
 * @param description    新描述（可空；最长 512）
 * @param defaultBranch  新默认分支（可空；1..128）
 * @param language       新主要语言（可空；命中枚举即可）
 */
public record ProjectUpdateRequest(
        @Size(max = 512) String description,
        @Size(max = 128) String defaultBranch,
        @Size(max = 32)
        @Pattern(regexp = "Java|Python|JavaScript|TypeScript|Go",
                 message = "language 必须为 Java / Python / JavaScript / TypeScript / Go 之一")
        String language
) {
}
