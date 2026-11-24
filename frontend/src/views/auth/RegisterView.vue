<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', displayName: '', password: '', confirm: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_.-]{3,32}$/, message: '3–32 位字母、数字、下划线、点或短横线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '长度需在 8–64 位之间', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '需同时包含字母与数字', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    const userInfo = await auth.signUp(form.username.trim(), form.password, form.displayName.trim() || undefined)
    ElMessage.success(`注册成功，${userInfo.displayName}`)
    router.replace(auth.homePath)
  } catch {
    /* 错误提示由 axios 拦截器统一弹出 */
  }
}
</script>

<template>
  <div class="register">
    <div class="head">
      <h2>创建账户</h2>
      <p>注册后为普通用户，可使用对话功能</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" size="large" placeholder="3–32 位，登录时使用" />
      </el-form-item>

      <el-form-item label="昵称（可选）">
        <el-input v-model="form.displayName" size="large" placeholder="不填就用用户名" />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" size="large" show-password placeholder="至少 8 位，含字母与数字" />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirm">
        <el-input
          v-model="form.confirm"
          type="password"
          size="large"
          show-password
          placeholder="再输入一次"
          @keyup.enter="submit"
        />
      </el-form-item>

      <el-button class="btn" type="primary" size="large" :loading="auth.loading" @click="submit">
        注册并登录
      </el-button>
    </el-form>

    <div class="footer">
      <p>已有账户？<router-link to="/auth/login">去登录</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.register { width: 100%; max-width: 360px; }

.head { text-align: center; margin-bottom: 24px; }
.head h2 { margin: 0 0 8px; font-size: 24px; font-weight: 600; color: #1d2129; }
.head p { margin: 0; font-size: 14px; color: #86909c; }

.btn { width: 100%; margin-top: 8px; }

.footer { margin-top: 20px; text-align: center; font-size: 13px; color: #86909c; }
.footer a { color: #4f46e5; text-decoration: none; }
.footer a:hover { text-decoration: underline; }
</style>
