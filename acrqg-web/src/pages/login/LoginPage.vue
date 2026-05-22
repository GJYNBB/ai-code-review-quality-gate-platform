<script setup lang="ts">
/**
 * UI-001 登录页（B5-A.2）。
 *
 * 关联需求：R1.1 / R1.2（登录）、R3.2（账号禁用即失效）。
 *
 * 行为：
 * 1. 表单（用户名 + 密码）+ 登录按钮（loading 态）
 * 2. 调 auth.login → 写入 auth store → 跳转 route.query.redirect 或 /dashboard
 * 3. 错误码定向提示：
 *    - AUTH_INVALID_CREDENTIALS → "用户名或密码错误"
 *    - AUTH_ACCOUNT_DISABLED   → "账号已被禁用"
 *
 * UI 通过 router 的 BlankLayout 容器呈现（/login → BlankLayout > LoginPage）。
 */

import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { login as loginApi } from '@/api/auth'
import { ApiBusinessError } from '@/api/http'
import { ERROR_MESSAGES } from '@/api/errorCodes'
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
        // 防止 redirect 指回登录页造成回环
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
        handleSubmit()
    }
}
</script>

<template>
  <el-card class="login-card" shadow="always">
    <template #header>
      <div class="login-card__header">
        <h2 class="login-card__title">AI 代码评审与质量门禁平台</h2>
        <p class="login-card__subtitle">面向研发团队的智能评审与门禁中枢</p>
      </div>
    </template>

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
          :prefix-icon="undefined"
          clearable
        />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
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
          登 录
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style lang="scss" scoped>
.login-card {
  border-radius: 12px;

  &__header {
    text-align: center;
  }

  &__title {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__subtitle {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  &__form {
    padding: 8px 4px 0;
  }

  &__submit {
    width: 100%;
  }
}
</style>
