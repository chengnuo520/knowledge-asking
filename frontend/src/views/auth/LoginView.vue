<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchCaptcha } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '', captchaCode: '' })

/** 验证码图片与 id，图片是 data URL，点一下就换一张 */
const captchaImage = ref('')
const captchaId = ref('')
const captchaLoading = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const issue = await fetchCaptcha()
    captchaId.value = issue.captchaId
    captchaImage.value = issue.image
    form.captchaCode = ''
  } catch {
    captchaImage.value = ''
  } finally {
    captchaLoading.value = false
  }
}

onMounted(refreshCaptcha)

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    const userInfo = await auth.signIn(
      form.username.trim(),
      form.password,
      captchaId.value,
      form.captchaCode.trim()
    )
    ElMessage.success(`欢迎回来，${userInfo.displayName}`)
    const redirect = route.query.redirect as string | undefined
    router.replace(redirect || auth.homePath)
  } catch {
    // 验证码是一次性的：无论失败原因是验证码还是密码，都得换一张
    await refreshCaptcha()
  }
}
</script>

<template>
  <div class="login">
    <div class="head">
      <h2>登录您的账户</h2>
      <p>请输入账号、密码与图片验证码</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" size="large" placeholder="例如 admin" autocomplete="username" />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          size="large"
          placeholder="请输入密码"
          show-password
          autocomplete="current-password"
        />
      </el-form-item>

      <el-form-item label="验证码" prop="captchaCode">
        <div class="captcha-row">
          <el-input
            v-model="form.captchaCode"
            size="large"
            maxlength="4"
            placeholder="4 位字符，不区分大小写"
            @keyup.enter="submit"
          />
          <div class="captcha-box" :class="{ loading: captchaLoading }" title="看不清？点击换一张" @click="refreshCaptcha">
            <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
            <span v-else class="captcha-fallback">点击重试</span>
          </div>
        </div>
      </el-form-item>

      <el-button class="btn" type="primary" size="large" :loading="auth.loading" @click="submit">
        登录
      </el-button>
    </el-form>

    <div class="footer">
      <p>还没有账户？<router-link to="/auth/register">去注册</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.login { width: 100%; max-width: 360px; }

.head { text-align: center; margin-bottom: 26px; }
.head h2 { margin: 0 0 8px; font-size: 24px; font-weight: 600; color: #1d2129; }
.head p { margin: 0; font-size: 14px; color: #86909c; }

.captcha-row { display: flex; gap: 10px; width: 100%; align-items: center; }
.captcha-row .el-input { flex: 1; }

.captcha-box {
  width: 120px;
  height: 40px;
  flex-shrink: 0;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f8fa;
  transition: border-color 0.15s;
}
.captcha-box:hover { border-color: #4f46e5; }
.captcha-box.loading { opacity: 0.5; }
.captcha-box img { width: 100%; height: 100%; object-fit: cover; display: block; }
.captcha-fallback { font-size: 12px; color: #86909c; }

.btn { width: 100%; margin-top: 12px; }

.footer { margin-top: 22px; text-align: center; font-size: 13px; color: #86909c; }
.footer a { color: #4f46e5; text-decoration: none; }
.footer a:hover { text-decoration: underline; }
</style>
