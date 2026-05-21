package com.acrqg.platform.diff.domain;

/**
 * 单行 diff 条目（design.md §6.4 / §7.2 中 hunks JSON 子结构）。
 *
 * <p>由 {@link com.acrqg.platform.diff.service.impl.DiffParserImpl} 在解析
 * unified diff 时构造，序列化进 {@code diff_file.hunks} JSONB 列。前端 / 报告页
 * 据此渲染左右两栏行号与内容（参考 design §16.4 报告页 diff view）。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@link #type}：行类型，参见 {@link Type}；</li>
 *   <li>{@link #content}：原始行内容（不含首字符 {@code +}/{@code -}/{@code (space)}）；</li>
 *   <li>{@link #oldLineNo}：旧文件中的行号（基于 hunk 头 oldStart 计算）；
 *       {@link Type#ADD} 行为 {@code null}；</li>
 *   <li>{@link #newLineNo}：新文件中的行号（基于 hunk 头 newStart 计算）；
 *       {@link Type#DEL} 行为 {@code null}。</li>
 * </ul>
 *
 * <p>{@link Type#HEADER} 仅用于承载 hunk 头本身的字符串内容（{@code @@ ... @@}），
 * 在 hunk.lines 中通常不出现——本枚举值留作未来扩展（DiffViewService 渲染锚点）。
 *
 * <p>Covers: R10.2, R10.3, R16.4。
 *
 * @param type      行类型
 * @param content   原始内容（不含 +/-/space 首字符）
 * @param oldLineNo 旧文件行号；{@code null} 表示新增行
 * @param newLineNo 新文件行号；{@code null} 表示删除行
 */
public record DiffLine(
        Type type,
        String content,
        Integer oldLineNo,
        Integer newLineNo
) {

    /** 行类型字典。 */
    public enum Type {
        /** 上下文行（' ' 前缀）。 */
        CONTEXT,
        /** 新增行（'+' 前缀）。 */
        ADD,
        /** 删除行（'-' 前缀）。 */
        DEL,
        /** hunk 头行（'@@ ... @@'）；当前实现不放入 lines，保留枚举供未来扩展。 */
        HEADER
    }

    /** 工厂：上下文行。 */
    public static DiffLine context(String content, int oldLineNo, int newLineNo) {
        return new DiffLine(Type.CONTEXT, content, oldLineNo, newLineNo);
    }

    /** 工厂：新增行（仅有 newLineNo）。 */
    public static DiffLine added(String content, int newLineNo) {
        return new DiffLine(Type.ADD, content, null, newLineNo);
    }

    /** 工厂：删除行（仅有 oldLineNo）。 */
    public static DiffLine deleted(String content, int oldLineNo) {
        return new DiffLine(Type.DEL, content, oldLineNo, null);
    }
}
