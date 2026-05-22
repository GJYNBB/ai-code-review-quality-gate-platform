<script setup lang="ts">
/**
 * UI-009 质量门禁配置（B5-A.7）。
 *
 * 关联需求：R13（门禁规则配置）。
 *
 * 功能：
 * - 顶部按钮：「使用默认模板」（gate.listTemplates 拉取后塞入本地表单）
 * - 动态规则表格：metric / operator / threshold / severity / enabled，可新增 / 删除 / 上下移动
 * - 顶部展示当前启用版本号 + 「历史版本」按钮（弹出版本列表）
 * - 「保存」失败若返回 GATE_RULE_INVALID + details[*].field（形如 rules[2].threshold）
 *   时，将该行高亮红色，并在行内显示对应错误信息
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowDown, ArrowUp, Delete, Plus, Refresh } from '@element-plus/icons-vue'

import * as gateApi from '@/api/gate'
import { ApiBusinessError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type {
  FieldError,
  GateMetric,
  GateOperator,
  GateRuleDTO,
  GateRuleSeverity,
  QualityGateDTO,
} from '@/types/api'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const METRICS: Array<{ label: string; value: GateMetric }> = [
  { label: '严重问题数 (critical_issue_count)', value: 'critical_issue_count' },
  { label: '安全问题数 (security_issue_count)', value: 'security_issue_count' },
  { label: '测试覆盖率 (test_coverage)', value: 'test_coverage' },
  { label: '重复率 (duplicate_rate)', value: 'duplicate_rate' },
  { label: 'AI 风险分 (ai_risk_score)', value: 'ai_risk_score' },
  { label: '新增问题数 (new_issue_count)', value: 'new_issue_count' },
]

const OPERATORS: Array<{ label: string; value: GateOperator }> = [
  { label: '>', value: 'GT' },
  { label: '>=', value: 'GTE' },
  { label: '<', value: 'LT' },
  { label: '<=', value: 'LTE' },
  { label: '=', value: 'EQ' },
  { label: '<>', value: 'NEQ' },
]

const SEVERITIES: Array<{ label: string; value: GateRuleSeverity }> = [
  { label: '阻断 (BLOCKER)', value: 'BLOCKER' },
  { label: '警告 (WARN)', value: 'WARN' },
]

interface RuleRow extends GateRuleDTO {
  /** 临时前端 id，用于 :key 与错误高亮 */
  _localId: number
}

let localIdSeq = 1
function toRow(rule: GateRuleDTO): RuleRow {
  return {
    _localId: localIdSeq++,
    id: rule.id ?? null,
    metric: rule.metric,
    operator: rule.operator,
    threshold: String(rule.threshold ?? ''),
    severity: (rule.severity as GateRuleSeverity) ?? 'BLOCKER',
    enabled: rule.enabled ?? true,
  }
}

function newEmptyRow(): RuleRow {
  return toRow({
    metric: 'critical_issue_count',
    operator: 'GT',
    threshold: '0',
    severity: 'BLOCKER',
    enabled: true,
  })
}

const loading = ref(false)
const saving = ref(false)
const enabled = ref<QualityGateDTO | null>(null)
const form = reactive<{ name: string; rules: RuleRow[] }>({
  name: '默认门禁',
  rules: [],
})
/** field error map: key=row index, value=error message */
const rowErrors = ref<Record<number, string>>({})

async function loadEnabled() {
  if (!Number.isFinite(projectId.value)) return
  loading.value = true
  try {
    enabled.value = await gateApi.getEnabled(projectId.value)
    if (enabled.value) {
      form.name = enabled.value.name || '默认门禁'
      form.rules = (enabled.value.rules ?? []).map(toRow)
    } else {
      form.name = '默认门禁'
      form.rules = []
    }
    rowErrors.value = {}
  } finally {
    loading.value = false
  }
}

async function applyTemplate() {
  try {
    const tpl = await gateApi.listTemplates()
    form.name = tpl.name || form.name
    form.rules = (tpl.rules ?? []).map(toRow)
    rowErrors.value = {}
    ElMessage.success('已载入默认模板规则')
  } catch (err) {
    if (err instanceof ApiBusinessError) ElMessage.error(err.message)
  }
}

function addRule() {
  form.rules.push(newEmptyRow())
}

function removeRule(idx: number) {
  form.rules.splice(idx, 1)
  rowErrors.value = {}
}

function moveUp(idx: number) {
  if (idx <= 0) return
  const tmp = form.rules[idx - 1]
  form.rules[idx - 1] = form.rules[idx]
  form.rules[idx] = tmp
  rowErrors.value = {}
}

function moveDown(idx: number) {
  if (idx >= form.rules.length - 1) return
  const tmp = form.rules[idx + 1]
  form.rules[idx + 1] = form.rules[idx]
  form.rules[idx] = tmp
  rowErrors.value = {}
}

function validateLocal(): boolean {
  if (form.rules.length === 0) {
    ElMessage.warning('至少需要 1 条规则')
    return false
  }
  for (let i = 0; i < form.rules.length; i++) {
    const r = form.rules[i]
    if (!r.metric || !r.operator || r.threshold === '' || r.threshold === null) {
      rowErrors.value = { ...rowErrors.value, [i]: '所有字段均为必填' }
      ElMessage.warning(`第 ${i + 1} 行存在未填写的字段`)
      return false
    }
    if (Number.isNaN(Number(r.threshold))) {
      rowErrors.value = { ...rowErrors.value, [i]: '阈值必须为数字' }
      ElMessage.warning(`第 ${i + 1} 行阈值需为数字`)
      return false
    }
  }
  return true
}

/**
 * 解析后端 details 中类似 `rules[2].threshold` 的字段路径，把 index 提取出来
 * 写入 rowErrors。任何不能匹配规则索引的错误，统一拼到 message 提示。
 */
function applyServerErrors(details: FieldError[] | undefined) {
  rowErrors.value = {}
  if (!details || details.length === 0) return
  const tail: string[] = []
  for (const item of details) {
    const m = /^rules\[(\d+)\]/.exec(item.field || '')
    if (m) {
      const idx = Number(m[1])
      const existing = rowErrors.value[idx]
      const msg = `${item.field.replace(/^rules\[\d+\]\.?/, '') || '规则'}：${item.reason}`
      rowErrors.value = {
        ...rowErrors.value,
        [idx]: existing ? `${existing}；${msg}` : msg,
      }
    } else {
      tail.push(`${item.field}：${item.reason}`)
    }
  }
  if (tail.length > 0) ElMessage.error(tail.join('；'))
}

async function handleSave() {
  if (!validateLocal()) return
  saving.value = true
  try {
    const saved = await gateApi.save(projectId.value, {
      name: form.name.trim() || '默认门禁',
      rules: form.rules.map((r) => ({
        id: r.id ?? null,
        metric: r.metric,
        operator: r.operator,
        threshold: String(r.threshold),
        severity: r.severity,
        enabled: r.enabled ?? true,
      })),
    })
    enabled.value = saved
    form.name = saved.name || form.name
    form.rules = (saved.rules ?? []).map(toRow)
    rowErrors.value = {}
    ElMessage.success(`保存成功，当前启用版本：v${saved.version ?? '-'}`)
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      if (err.code === 'GATE_RULE_INVALID') {
        applyServerErrors(err.details as FieldError[] | undefined)
        ElMessage.error('门禁规则配置非法，请检查标红行')
      } else {
        ElMessage.error(err.message)
      }
    }
  } finally {
    saving.value = false
  }
}

// ---- 历史版本对话框 ----
const versionsDialogVisible = ref(false)
const versionsLoading = ref(false)
const versions = ref<QualityGateDTO[]>([])

async function openVersions() {
  versionsDialogVisible.value = true
  versionsLoading.value = true
  try {
    versions.value = await gateApi.listVersions(projectId.value)
  } catch {
    versions.value = []
    ElMessage.warning('暂无法获取历史版本（接口未实现或无权访问）')
  } finally {
    versionsLoading.value = false
  }
}

async function loadVersion(row: QualityGateDTO) {
  if (!row.id) return
  try {
    const detail = await gateApi.getVersion(row.id)
    form.name = detail.name || form.name
    form.rules = (detail.rules ?? []).map(toRow)
    rowErrors.value = {}
    ElMessage.success(`已载入版本 v${detail.version ?? '-'}（请确认后保存以启用）`)
    versionsDialogVisible.value = false
  } catch (err) {
    if (err instanceof ApiBusinessError) ElMessage.error(err.message)
  }
}

onMounted(() => {
  void loadEnabled()
})

watch(projectId, () => {
  void loadEnabled()
})
</script>

<template>
  <div class="quality-gate-page" v-loading="loading">
    <el-card shadow="never" class="quality-gate-page__header">
      <div class="header-row">
        <div>
          <h3 class="header-row__title">质量门禁配置</h3>
          <div class="header-row__sub">
            当前启用版本：
            <el-tag v-if="enabled?.version" type="success" size="small">v{{ enabled.version }}</el-tag>
            <span v-else class="text-secondary">未配置</span>
            <span v-if="enabled?.createdAt" class="text-secondary">
              · 创建于 {{ formatDateTime(enabled.createdAt) }}
            </span>
          </div>
        </div>
        <div class="header-row__spacer" />
        <el-button :icon="Refresh" @click="loadEnabled">刷新</el-button>
        <el-button :icon="ArrowLeft" @click="openVersions">历史版本</el-button>
        <el-button :icon="Plus" @click="applyTemplate">使用默认模板</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="quality-gate-page__form">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="form.name" maxlength="128" style="max-width: 360px" />
        </el-form-item>
      </el-form>

      <el-table :data="form.rules" border :row-class-name="({ rowIndex }) => (rowErrors[rowIndex] ? 'rule-row-error' : '')">
        <el-table-column type="index" label="顺序" width="80" />
        <el-table-column label="指标 metric" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.metric" style="width: 100%">
              <el-option v-for="m in METRICS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="比较 operator" width="120">
          <template #default="{ row }">
            <el-select v-model="row.operator" style="width: 100%">
              <el-option v-for="o in OPERATORS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="阈值 threshold" width="160">
          <template #default="{ row }">
            <el-input v-model="row.threshold" placeholder="数字" />
          </template>
        </el-table-column>
        <el-table-column label="严重度 severity" width="160">
          <template #default="{ row }">
            <el-select v-model="row.severity" style="width: 100%">
              <el-option v-for="s in SEVERITIES" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button :icon="ArrowUp" link size="small" :disabled="$index === 0" @click="moveUp($index)" />
            <el-button
              :icon="ArrowDown"
              link
              size="small"
              :disabled="$index === form.rules.length - 1"
              @click="moveDown($index)"
            />
            <el-button :icon="Delete" link size="small" type="danger" @click="removeRule($index)" />
          </template>
        </el-table-column>
        <el-table-column label="错误" min-width="240">
          <template #default="{ $index }">
            <span v-if="rowErrors[$index]" class="rule-row-error__text">{{ rowErrors[$index] }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="quality-gate-page__actions">
        <el-button :icon="Plus" @click="addRule">新增规则</el-button>
        <div class="quality-gate-page__spacer" />
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </el-card>

    <el-dialog v-model="versionsDialogVisible" title="历史版本" width="640px">
      <el-table v-loading="versionsLoading" :data="versions">
        <el-table-column prop="version" label="版本" width="100">
          <template #default="{ row }">v{{ row.version ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="名称" />
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">启用中</el-tag>
            <el-tag v-else type="info" size="small">历史</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="loadVersion(row)">载入</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.quality-gate-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .header-row {
    display: flex;
    align-items: center;
    gap: 12px;

    &__title {
      margin: 0 0 4px 0;
      font-size: 16px;
      font-weight: 600;
    }

    &__sub {
      color: var(--el-text-color-secondary);
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    &__spacer {
      flex: 1 1 auto;
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 12px;
  }

  &__spacer {
    flex: 1 1 auto;
  }
}

.text-secondary {
  color: var(--el-text-color-secondary);
}

.rule-row-error__text {
  color: var(--el-color-danger);
  font-size: 12px;
}

:deep(.rule-row-error) {
  background: var(--el-color-danger-light-9) !important;
  td {
    border-color: var(--el-color-danger-light-5);
  }
}
</style>
