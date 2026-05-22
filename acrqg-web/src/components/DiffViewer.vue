<script setup lang="ts">
/**
 * DiffViewer（B5-A.9）。
 *
 * 关联需求：R16.4。
 *
 * 入参：files: DiffFileDTO[]
 *
 * 行为：
 * - 每个文件可折叠（el-collapse），头部展示 filePath / +N / -N / changeType
 * - hunk 内逐行渲染：行号双列（旧 / 新）+ 行内容
 *   - "+" 开头：绿色背景，新行号递增
 *   - "-" 开头：红色背景，旧行号递增
 *   - 否则：上下文行，两侧行号同时递增
 * - hunk header（@@ -a,b +c,d @@）单独一行展示
 */
import { computed } from 'vue'
import type { DiffFileDTO, DiffHunkDTO } from '@/types/api'

const props = defineProps<{
  files: DiffFileDTO[]
}>()

interface RenderedLine {
  type: 'context' | 'add' | 'del' | 'header'
  oldNo: number | ''
  newNo: number | ''
  content: string
}

function renderHunk(h: DiffHunkDTO): RenderedLine[] {
  const lines: RenderedLine[] = []
  const headerText =
    h.header && h.header.trim().length > 0
      ? h.header
      : `@@ -${h.oldStart},${h.oldLines} +${h.newStart},${h.newLines} @@`
  lines.push({ type: 'header', oldNo: '', newNo: '', content: headerText })

  let oldNo = h.oldStart
  let newNo = h.newStart
  for (const raw of h.lines ?? []) {
    const first = raw.charAt(0)
    if (first === '+') {
      lines.push({ type: 'add', oldNo: '', newNo, content: raw.slice(1) })
      newNo++
    } else if (first === '-') {
      lines.push({ type: 'del', oldNo, newNo: '', content: raw.slice(1) })
      oldNo++
    } else {
      // context（可能以空格开头）
      const content = first === ' ' ? raw.slice(1) : raw
      lines.push({ type: 'context', oldNo, newNo, content })
      oldNo++
      newNo++
    }
  }
  return lines
}

const renderedFiles = computed(() =>
  (props.files ?? []).map((f) => ({
    ...f,
    renderedHunks: (f.hunks ?? []).map(renderHunk),
  })),
)

const collapseValue = computed(() => renderedFiles.value.map((f) => f.filePath))
</script>

<template>
  <div class="diff-viewer">
    <el-empty v-if="renderedFiles.length === 0" description="暂无差异" />
    <el-collapse v-else :model-value="collapseValue">
      <el-collapse-item v-for="file in renderedFiles" :key="file.filePath" :name="file.filePath">
        <template #title>
          <div class="diff-viewer__file-header">
            <span class="diff-viewer__file-path">{{ file.filePath }}</span>
            <el-tag v-if="file.changeType" size="small" type="info" effect="plain">
              {{ file.changeType }}
            </el-tag>
            <span class="diff-viewer__added">+{{ file.addedLines ?? 0 }}</span>
            <span class="diff-viewer__deleted">-{{ file.deletedLines ?? 0 }}</span>
          </div>
        </template>

        <div class="diff-viewer__hunks">
          <div
            v-for="(hunk, hIdx) in file.renderedHunks"
            :key="`${file.filePath}-h-${hIdx}`"
            class="diff-viewer__hunk"
          >
            <div
              v-for="(line, lIdx) in hunk"
              :key="`${file.filePath}-h-${hIdx}-l-${lIdx}`"
              class="diff-viewer__line"
              :class="{
                'is-add': line.type === 'add',
                'is-del': line.type === 'del',
                'is-header': line.type === 'header',
                'is-context': line.type === 'context',
              }"
            >
              <span class="diff-viewer__lineno diff-viewer__lineno--old">{{ line.oldNo }}</span>
              <span class="diff-viewer__lineno diff-viewer__lineno--new">{{ line.newNo }}</span>
              <span class="diff-viewer__sign">
                <template v-if="line.type === 'add'">+</template>
                <template v-else-if="line.type === 'del'">-</template>
                <template v-else>&nbsp;</template>
              </span>
              <span class="diff-viewer__content">{{ line.content }}</span>
            </div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style lang="scss" scoped>
.diff-viewer {
  font-family: 'Source Code Pro', Consolas, 'Courier New', monospace;
  font-size: 13px;

  &__file-header {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
  }

  &__file-path {
    font-weight: 600;
    word-break: break-all;
  }

  &__added {
    color: var(--el-color-success);
  }
  &__deleted {
    color: var(--el-color-danger);
  }

  &__hunk + &__hunk {
    margin-top: 8px;
    border-top: 1px dashed var(--el-border-color-light);
    padding-top: 8px;
  }

  &__line {
    display: flex;
    align-items: stretch;
    line-height: 1.5;
    white-space: pre;

    &.is-add {
      background: #e6ffed;
    }
    &.is-del {
      background: #ffeef0;
    }
    &.is-header {
      background: var(--el-fill-color-light);
      color: var(--el-text-color-secondary);
    }
  }

  &__lineno {
    flex: 0 0 56px;
    text-align: right;
    padding: 0 8px;
    color: var(--el-text-color-secondary);
    user-select: none;
    border-right: 1px solid var(--el-border-color-lighter);

    &--new {
      border-right: 1px solid var(--el-border-color-lighter);
    }
  }

  &__sign {
    flex: 0 0 18px;
    text-align: center;
    color: var(--el-text-color-secondary);
  }

  &__content {
    flex: 1 1 auto;
    padding-right: 8px;
    overflow-x: auto;
  }
}
</style>
