<script setup lang="ts">
/**
 * UI-001 登录页。
 * 保留原登录行为，重写为 Aegis Console 产品入口。
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'

import { login as loginApi } from '@/api/auth'
import { ERROR_MESSAGES } from '@/api/errorCodes'
import { ApiBusinessError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度需在 3-64 之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 128, message: '密码长度需在 8-128 之间', trigger: 'blur' },
  ],
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const result = await loginApi({
      username: form.username.trim(),
      password: form.password,
    })
    authStore.setSession({
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      expiresIn: result.expiresIn,
      user: result.user,
    })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect.startsWith('/login') ? '/dashboard' : redirect)
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      const code = String(err.code)
      if (code === 'AUTH_INVALID_CREDENTIALS') {
        ElMessage.error(ERROR_MESSAGES.AUTH_INVALID_CREDENTIALS)
      } else if (code === 'AUTH_ACCOUNT_DISABLED') {
        ElMessage.error(ERROR_MESSAGES.AUTH_ACCOUNT_DISABLED)
      } else {
        ElMessage.error(err.message)
      }
    } else {
      ElMessage.error('登录失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    void handleSubmit()
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-page__story">
      <div class="login-logo">AC</div>
      <p class="login-page__eyebrow">AI Review Quality Gate</p>
      <h1>把代码评审、静态扫描与质量门禁收束到一个安全控制台。</h1>
      <p class="login-page__lead">
        连接 Git 平台、Redis Stream Worker、SAST 与 AI 评审，持续追踪项目质量风险。
      </p>
      <div class="login-page__metrics">
        <div>
          <strong>4-stage</strong>
          <span>异步评审流水线</span>
        </div>
        <div>
          <strong>Zero-trust</strong>
          <span>项目级授权边界</span>
        </div>
        <div>
          <strong>AI + SAST</strong>
          <span>智能风险聚合</span>
        </div>
      </div>
    </div>

    <el-card class="login-card" shadow="never">
      <div class="login-card__header">
        <span class="login-card__badge">Secure sign in</span>
        <h2>登录控制台</h2>
        <p>使用平台账号进入 ACRQG 运维与质量看板。</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="login-card__form"
        @keydown="handleKeydown"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            autocomplete="username"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-card__submit"
            :loading="submitting"
            @click="handleSubmit"
          >
            进入控制台
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </section>
</template>

<style lang="scss" scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(380px, 0.88fr);
  gap: 28px;
  align-items: stretch;

  &__story,
  .login-card {
    border: 1px solid rgba(255, 255, 255, 0.18);
    border-radius: 30px;
    backdrop-filter: blur(18px);
  }

  &__story {
    min-height: 560px;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    padding: 42px;
    color: #fff;
    background:
      linear-gradient(180deg, rgba(15, 23, 42, 0.16), rgba(15, 23, 42, 0.76)),
      radial-gradient(circle at 72% 18%, rgba(6, 182, 212, 0.28), transparent 26%),
      rgba(255, 255, 255, 0.06);
    box-shadow: 0 30px 80px rgba(0, 0, 0, 0.22);
  }

  &__eyebrow {
    margin: 28px 0 12px;
    color: #67e8f9;
    font-size: 12px;
    font-weight: 850;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }

  h1 {
    max-width: 760px;
    margin: 0;
    font-size: clamp(34px, 4.5vw, 58px);
    line-height: 1.02;
    letter-spacing: -0.06em;
  }

  &__lead {
    max-width: 620px;
    margin: 18px 0 0;
    color: rgba(226, 232, 240, 0.78);
    font-size: 16px;
    line-height: 1.8;
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
    margin-top: 34px;

    div {
      padding: 16px;
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.08);
    }

    strong,
    span {
      display: block;
    }

    strong {
      font-size: 18px;
    }

    span {
      margin-top: 4px;
      color: rgba(226, 232, 240, 0.64);
      font-size: 12px;
    }
  }
}

.login-logo {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  color: #fff;
  background: linear-gradient(135deg, var(--acrqg-accent), var(--acrqg-accent-2));
  box-shadow: 0 24px 60px rgba(6, 182, 212, 0.28);
  font-size: 20px;
  font-weight: 900;
  letter-spacing: -0.06em;
}

.login-card {
  align-self: center;
  padding: 14px;
  background: rgba(255, 255, 255, 0.94) !important;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.18) !important;

  &__header {
    padding: 10px 6px 18px;

    h2 {
      margin: 14px 0 8px;
      color: var(--acrqg-text-primary);
      font-size: 28px;
      letter-spacing: -0.04em;
    }

    p {
      margin: 0;
      color: var(--acrqg-text-secondary);
      line-height: 1.6;
    }
  }

  &__badge {
    display: inline-flex;
    padding: 6px 10px;
    border-radius: 999px;
    color: var(--acrqg-accent);
    background: var(--acrqg-accent-soft);
    font-size: 12px;
    font-weight: 850;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  &__form {
    padding-top: 8px;
  }

  &__submit {
    width: 100%;
    height: 44px;
    margin-top: 6px;
  }
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;

    &__story {
      min-height: auto;
      padding: 28px;
    }

    &__metrics {
      grid-template-columns: 1fr;
    }
  }
}
</style>
