<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-left">
        <div class="platform-title">
          <div class="title-line">创万联</div>
          <div class="title-line">InteliConnect</div>
          <div class="title-line">物联网平台</div>
        </div>

      </div>
      <div class="login-form">
        <h2 class="welcome-text">欢迎回来</h2>
        <p class="sub-title">请登录您的账户</p>

        <!-- 登录方式选项卡 -->
        <div class="login-tabs">
          <div
            class="login-tab"
            :class="{ active: loginMode === 'account' }"
            @click="switchMode('account')"
          >
            <UserOutlined />
            <span>账号登录</span>
          </div>
          <div
            class="login-tab"
            :class="{ active: loginMode === 'wechat' }"
            @click="switchMode('wechat')"
          >
            <WechatOutlined />
            <span>微信扫码</span>
          </div>
        </div>

        <!-- 账号密码登录 -->
        <div v-show="loginMode === 'account'">
          <a-form :model="loginForm" @finish="handleSubmit" class="login-form-content">
            <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
              <a-input v-model:value="loginForm.username" size="large" placeholder="用户名" autocomplete="off">
                <template #prefix>
                  <UserOutlined class="site-form-item-icon" />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }]">
              <a-input-password v-model:value="loginForm.password" size="large" placeholder="密码" autocomplete="off">
                <template #prefix>
                  <LockOutlined class="site-form-item-icon" />
                </template>
              </a-input-password>
            </a-form-item>

            <div class="login-options">
              <a-checkbox v-model:checked="rememberMe">记住我</a-checkbox>
              <router-link to="/forgotPassword" class="forgot-link">忘记密码？</router-link>
            </div>

            <a-form-item>
              <a-button type="primary" html-type="submit" class="login-button" size="large" :loading="loading">
                登录
              </a-button>
            </a-form-item>
          </a-form>
        </div>

        <!-- 微信扫码登录 -->
        <div v-show="loginMode === 'wechat'" class="wechat-scan-area">
          <!-- 加载中 -->
          <div v-if="qrLoading" class="qr-placeholder">
            <a-spin size="large" />
            <p class="qr-tip">正在获取二维码...</p>
          </div>

          <!-- 获取失败 -->
          <div v-else-if="qrError" class="qr-placeholder">
            <div class="qr-error-icon">!</div>
            <p class="qr-tip qr-error-text">{{ qrError }}</p>
            <a-button type="primary" size="small" @click="fetchQrCode">重新获取</a-button>
          </div>

          <!-- 二维码展示 -->
          <div v-else-if="qrCodeUrl" class="qr-wrapper">
            <!-- 已过期遮罩 -->
            <div v-if="scanStatus === 'expired'" class="qr-expired-overlay" @click="fetchQrCode">
              <div class="expired-content">
                <ReloadOutlined class="expired-icon" />
                <p>二维码已过期</p>
                <p>点击刷新</p>
              </div>
            </div>
            <img :src="qrCodeUrl" alt="微信扫码登录" class="qr-image" />
          </div>

          <!-- 扫码状态提示 -->
          <div class="scan-status-area">
            <p v-if="scanStatus === 'waiting'" class="qr-tip">
              <WechatOutlined class="wechat-icon-green" />
              请使用微信扫描二维码登录
            </p>
            <p v-else-if="scanStatus === 'scanned'" class="qr-tip qr-success-text">
              <CheckCircleOutlined class="success-icon" />
              扫码成功，正在登录...
            </p>
          </div>

          <!-- 倒计时 -->
          <p v-if="scanStatus === 'waiting' && countdown > 0" class="countdown-text">
            二维码有效期：{{ Math.floor(countdown / 60) }}:{{ String(countdown % 60).padStart(2, '0') }}
          </p>
        </div>

        <div class="register-link">
          还没有账号? <router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-box {
  display: flex;
  background: white;
  border-radius: 20px;
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.1);
  width: 1000px;
  max-width: 100%;
  min-height: 600px;
  overflow: hidden;
}

.login-left {
  position: relative;
  flex: 1;
  background: #f5f7ff;
  padding: 40px;
  display: flex;
  align-items: center;
  justify-content: center;

  .platform-title {
    position: absolute;
    top: 80px;
    left: 50px;
    z-index: 2;

    .title-line {
      font-size: 36px;
      font-weight: bold;
      color: #667eea;
      line-height: 1.5;
      text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);

      &:nth-child(2) {
        font-size: 42px;
        background: linear-gradient(90deg, #667eea, #764ba2);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
      }

      &:nth-child(3) {
        font-size: 32px;
      }
    }
  }

  .bg-image {
    width: 100%;
    max-width: 400px;
    height: auto;
    opacity: 0.6;
  }

  @media (max-width: 768px) {
    display: none;
  }
}

.login-form {
  flex: 1;
  padding: 50px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.welcome-text {
  font-size: 32px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.sub-title {
  color: #666;
  font-size: 16px;
  margin-bottom: 24px;
}

/* 选项卡样式 */
.login-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 30px;
  border-bottom: 2px solid #f0f0f0;
}

.login-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 0;
  cursor: pointer;
  color: #999;
  font-size: 15px;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.3s;
  user-select: none;

  &:hover {
    color: #667eea;
  }

  &.active {
    color: #667eea;
    border-bottom-color: #667eea;
    font-weight: 500;
  }
}

.login-form-content {
  max-width: 380px;
}

.site-form-item-icon {
  color: #bfbfbf;
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.forgot-link {
  color: #1890ff;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

.login-button {
  width: 100%;
  height: 45px;
  border-radius: 6px;
  font-size: 16px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border: none;

  &:hover {
    background: linear-gradient(90deg, #764ba2 0%, #667eea 100%);
  }
}

:deep(.ant-input-affix-wrapper) {
  border-radius: 6px;
  padding: 8px 11px;
}

:deep(.ant-checkbox-wrapper) {
  color: #666;
}

.register-link {
  text-align: center;
  margin-top: 16px;
  color: #666;

  a {
    color: #1890ff;

    &:hover {
      color: #40a9ff;
    }
  }
}

/* 微信扫码区域 */
.wechat-scan-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 320px;
  justify-content: center;
}

.qr-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 220px;
  height: 220px;
}

.qr-error-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #ff4d4f;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 12px;
}

.qr-wrapper {
  position: relative;
  width: 220px;
  height: 220px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
}

.qr-image {
  width: 200px;
  height: 200px;
  object-fit: contain;
}

.qr-expired-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.95);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;

  .expired-content {
    text-align: center;
    color: #999;

    .expired-icon {
      font-size: 36px;
      color: #667eea;
      margin-bottom: 8px;
    }

    p {
      margin: 4px 0;
      font-size: 14px;
    }
  }

  &:hover {
    .expired-icon {
      color: #764ba2;
    }
  }
}

.scan-status-area {
  margin-top: 16px;
  text-align: center;
}

.qr-tip {
  color: #666;
  font-size: 14px;
  margin: 8px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.qr-error-text {
  color: #ff4d4f;
}

.qr-success-text {
  color: #52c41a;
}

.wechat-icon-green {
  color: #07c160;
  font-size: 18px;
}

.success-icon {
  color: #52c41a;
  font-size: 18px;
}

.countdown-text {
  color: #999;
  font-size: 12px;
  margin-top: 8px;
  text-align: center;
}
</style>

<script setup>
import { ref, reactive, onBeforeUnmount } from 'vue'
import {
  UserOutlined,
  LockOutlined,
  WechatOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons-vue'
import { loginIn, wxScanLoginQrcode, wxScanLoginCheck } from '@/api/user'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { message } from 'ant-design-vue'
import { resolveTargetPath } from '@/utils/domain'

const router = useRouter()
const route = useRoute()
const store = useStore()

// ========== 通用状态 ==========
const loginMode = ref('account') // 'account' | 'wechat'

// ========== 账号登录 ==========
const loginForm = reactive({
  username: '',
  password: '',
})
const loading = ref(false)
const rememberMe = ref(false)

const handleSubmit = (values) => {
  loading.value = true
  loginIn(values)
    .then(async (res) => {
      const { data, errorCode } = res.data
      if (errorCode === 200) {
        store.commit('auth/SET_AUTH', data)
        await store.dispatch('domain/fetchDomainConfig')
        const domainState = store.state.domain
        store.commit('auth/GENERATE_ROUTES', { auth: data, domainState })
        const redirectTarget = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect
        router.replace(resolveTargetPath(domainState, redirectTarget))
      } else if (errorCode === 2007) {
        message.warn('账号不存在')
      } else if (errorCode === 2003) {
        message.warn('密码错误')
      } else {
        message.warn('系统错误，请重新登录')
      }
    })
    .catch((err) => {
      console.log(err)
    })
    .finally(() => {
      loading.value = false
    })
}

// ========== 微信扫码登录 ==========
const qrCodeUrl = ref('')
const sceneId = ref('')
const scanStatus = ref('') // 'waiting' | 'scanned' | 'expired'
const qrLoading = ref(false)
const qrError = ref('')
const countdown = ref(0)

let pollTimer = null
let countdownTimer = null

// 切换登录模式
const switchMode = (mode) => {
  if (loginMode.value === mode) return
  loginMode.value = mode
  if (mode === 'wechat' && !qrCodeUrl.value) {
    fetchQrCode()
  }
}

// 获取二维码
const fetchQrCode = async () => {
  // 先清理旧状态
  stopPolling()
  qrCodeUrl.value = ''
  sceneId.value = ''
  scanStatus.value = ''
  qrError.value = ''
  qrLoading.value = true
  countdown.value = 0

  try {
    const res = await wxScanLoginQrcode()
    const { data, errorCode, message: msg } = res.data
    if (errorCode === 200 && data) {
      qrCodeUrl.value = data.qrCodeUrl
      sceneId.value = data.sceneId
      countdown.value = data.expireSeconds || 300
      scanStatus.value = 'waiting'
      startPolling()
      startCountdown()
    } else {
      qrError.value = msg || '获取二维码失败，请稍后重试'
    }
  } catch (err) {
    console.error('获取二维码失败:', err)
    qrError.value = '网络异常，请检查网络后重试'
  } finally {
    qrLoading.value = false
  }
}

// 轮询检查扫码状态
const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!sceneId.value || scanStatus.value === 'expired' || scanStatus.value === 'scanned') {
      stopPolling()
      return
    }
    try {
      const res = await wxScanLoginCheck(sceneId.value)
      const { data, errorCode } = res.data
      if (errorCode === 200 && data) {
        if (data.status === 'scanned' && data.token) {
          scanStatus.value = 'scanned'
          stopPolling()
          stopCountdown()
          // 登录成功 - 使用扫码返回的 token
          await handleWxLoginSuccess(data.token)
        } else if (data.status === 'expired') {
          scanStatus.value = 'expired'
          stopPolling()
          stopCountdown()
        }
        // 'waiting' 继续轮询
      }
    } catch (err) {
      console.error('轮询扫码状态异常:', err)
    }
  }, 2500) // 每 2.5 秒轮询一次
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 倒计时
const startCountdown = () => {
  stopCountdown()
  countdownTimer = setInterval(() => {
    if (countdown.value <= 1) {
      countdown.value = 0
      scanStatus.value = 'expired'
      stopCountdown()
      stopPolling()
    } else {
      countdown.value--
    }
  }, 1000)
}

const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

// 微信扫码登录成功处理
const handleWxLoginSuccess = async (token) => {
  try {
    store.commit('auth/SET_AUTH', token)
    await store.dispatch('domain/fetchDomainConfig')
    const domainState = store.state.domain
    store.commit('auth/GENERATE_ROUTES', { auth: token, domainState })
    const redirectTarget = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect
    message.success('登录成功')
    router.replace(resolveTargetPath(domainState, redirectTarget))
  } catch (err) {
    console.error('微信登录跳转失败:', err)
    message.error('登录成功但跳转失败，请刷新页面')
  }
}

// 组件卸载时清理
onBeforeUnmount(() => {
  stopPolling()
  stopCountdown()
})
</script>
