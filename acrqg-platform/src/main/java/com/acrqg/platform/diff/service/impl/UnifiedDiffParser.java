package com.acrqg.platform.diff.service.impl;

import com.acrqg.platform.diff.domain.DiffHunk;
import com.acrqg.platform.diff.domain.DiffLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified diff 文本 → {@link DiffHunk} 列表 + 增删行数 解析器（纯函数）。
 *
 * <p>识别格式：
 * <pre>
 *   diff --git a/path b/path                  ← 文件级头（被忽略）
 *   --- a/old_path                           ← 旧文件标记（计数时跳过）
 *   +++ b/new_path                           ← 新文件标记（计数时跳过）
 *   @@ -oldStart,oldLines +newStart,newLines @@ optional context
 *   ' context line'                          ← CONTEXT
 *   '+ added line'                           ← ADD
 *   '- deleted line'                         ← DEL
 *   '\\ No newline at end of file'           ← 忽略
 * </pre>
 *
 * <p>默认行数 1（unified diff 规范允许省略 lines 字段：{@code @@ -1 +1 @@}）。
 *
 * <p>本类仅处理一个文件的 patch 文本；多文件按 {@link DiffFilePayload} 列表
 * 在外层逐个调用本类。
 *
 * <p>Covers: R10.2, R10.3。
 */
final class UnifiedDiffParser {

    /** {@code @@ -a[,b] +c[,d] @@ optional} 的头行匹配。 */
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private UnifiedDiffParser() {
    }

    /**
     * 解析单文件 unified diff 文本。
     *
     * @param patch 原始 diff 文本，可为 {@code null} / 空（视为无 hunk）
     * @return {@link Result}，永不为 {@code null}
     */
    static Result parse(String patch) {
        if (patch == null || patch.isEmpty()) {
            return new Result(0, 0, Collections.emptyList());
        }
        // 用 \n / \r\n / \r 都能切，保留空行
        String[] lines = patch.split("\\r?\\n", -1);

        List<DiffHunk> hunks = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;

        // 当前 hunk 状态
        int hunkOldStart = 0;
        int hunkOldLines = 0;
        int hunkNewStart = 0;
        int hunkNewLines = 0;
        List<DiffLine> hunkLines = null;
        int oldCursor = 0;
        int newCursor = 0;

        for (String line : lines) {
            // 跳过文件级头与空尾
            if (line.startsWith("diff --git ")
                    || line.startsWith("index ")
                    || line.startsWith("new file mode ")
                    || line.startsWith("deleted file mode ")
                    || line.startsWith("rename from ")
                    || line.startsWith("rename to ")
                    || line.startsWith("similarity index ")
                    || line.startsWith("dissimilarity index ")
                    || line.startsWith("Binary files ")) {
                continue;
            }
            // 跳过 --- / +++ 头（不计入 added/deleted）
            if (line.startsWith("--- ") || line.startsWith("+++ ")) {
                continue;
            }
            // hunk 头：开新 hunk
            Matcher m = HUNK_HEADER.matcher(line);
            if (m.matches()) {
                // 收尾上一个 hunk
                if (hunkLines != null) {
                    hunks.add(new DiffHunk(
                            hunkOldStart, hunkOldLines,
                            hunkNewStart, hunkNewLines,
                            hunkLines));
                }
                hunkOldStart = parseIntSafe(m.group(1), 0);
                hunkOldLines = parseIntSafe(m.group(2), 1);
                hunkNewStart = parseIntSafe(m.group(3), 0);
                hunkNewLines = parseIntSafe(m.group(4), 1);
                hunkLines = new ArrayList<>();
                oldCursor = hunkOldStart;
                newCursor = hunkNewStart;
                continue;
            }
            // 在 hunk 头之前的内容（罕见：GitHub 的 patch 字段直接以 @@ 开头；
            // 如果是 GitLab 的 diff 字段也可能以 @@ 开头）。本分支兜底跳过。
            if (hunkLines == null) {
                continue;
            }

            // 行体处理
            if (line.startsWith("\\")) {
                // "\ No newline at end of file" 等元数据行，忽略
                continue;
            }
            if (line.isEmpty()) {
                // 解析 splitlines 的尾部空字符串：通常源于以 \n 结尾的 patch；
                // 不计入 hunk lines（也不会推进游标）
                continue;
            }
            char head = line.charAt(0);
            String content = line.length() > 1 ? line.substring(1) : "";
            switch (head) {
                case '+' -> {
                    hunkLines.add(DiffLine.added(content, newCursor));
                    newCursor++;
                    totalAdded++;
                }
                case '-' -> {
                    hunkLines.add(DiffLine.deleted(content, oldCursor));
                    oldCursor++;
                    totalDeleted++;
                }
                case ' ' -> {
                    hunkLines.add(DiffLine.context(content, oldCursor, newCursor));
                    oldCursor++;
                    newCursor++;
                }
                default -> {
                    // 兜底：未知首字符按上下文行处理（非常宽松，避免漏行）
                    hunkLines.add(DiffLine.context(line, oldCursor, newCursor));
                    oldCursor++;
                    newCursor++;
                }
            }
        }
        // 收尾最后一个 hunk
        if (hunkLines != null) {
            hunks.add(new DiffHunk(
                    hunkOldStart, hunkOldLines,
                    hunkNewStart, hunkNewLines,
                    hunkLines));
        }
        return new Result(totalAdded, totalDeleted, hunks);
    }

    private static int parseIntSafe(String s, int fallback) {
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** 解析结果。 */
    record Result(int addedLines, int deletedLines, List<DiffHunk> hunks) {

        Result {
            hunks = hunks == null ? Collections.emptyList() : List.copyOf(hunks);
        }
    }
}
