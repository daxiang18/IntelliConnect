<template>
  <div class="detail-container">
    <div class="detail-header">
      <a-button type="link" @click="goBack" class="back-btn">
        <template #icon><ArrowLeftOutlined /></template>
        返回
      </a-button>
      <h2>消息详情</h2>
    </div>

    <a-spin :spinning="loading">
      <div v-if="!loading && !msg" class="detail-empty">
        <a-result status="404" title="消息不存在" sub-title="该消息可能已被删除或您没有访问权限">
          <template #extra>
            <a-button type="primary" @click="$router.push('/inbox')">返回收件箱</a-button>
          </template>
        </a-result>
      </div>

      <div v-if="msg" class="detail-content">
        <!-- 状态和元信息 -->
        <div class="detail-meta-card">
          <div class="meta-row">
            <span class="meta-label">状态</span>
            <a-tag :color="statusColor(msg.status)" size="large">{{ statusLabel(msg.status) }}</a-tag>
          </div>
          <div class="meta-row">
            <span class="meta-label">来源</span>
            <a-tag :color="sourceTypeColor(msg.sourceType)">{{ sourceTypeLabel(msg.sourceType) }}</a-tag>
          </div>
          <div class="meta-row">
            <span class="meta-label">内容类型</span>
            <a-tag>{{ contentTypeLabel(msg.contentType) }}</a-tag>
          </div>
          <div class="meta-row">
            <span class="meta-label">文档用途</span>
            <a-select
              :value="msg.documentPurpose || undefined"
              placeholder="选择用途"
              style="width: 140px"
              size="small"
              allowClear
              @change="handlePurposeChange"
            >
              <a-select-option value="quick_capture">速记</a-select-option>
              <a-select-option value="study_doc">学习文档</a-select-option>
              <a-select-option value="work_doc">工作文档</a-select-option>
              <a-select-option value="life_record">生活记录</a-select-option>
            </a-select>
          </div>
          <div class="meta-row" v-if="msg.category">
            <span class="meta-label">自动分类</span>
            <a-tag size="small">{{ msg.category }}</a-tag>
            <template v-if="msg.tags && msg.tags.length > 0">
              <a-tag v-for="tag in msg.tags" :key="tag" size="small" color="default">{{ tag }}</a-tag>
            </template>
          </div>
          <div class="meta-row">
            <span class="meta-label">接收时间</span>
            <span class="meta-value">{{ formatFullTime(msg.receivedAt) }}</span>
          </div>
          <div class="meta-row" v-if="msg.sessionId">
            <span class="meta-label">会话 ID</span>
            <span class="meta-value mono">{{ msg.sessionId }}</span>
          </div>
          <div class="meta-row" v-if="msg.senderId">
            <span class="meta-label">发送者</span>
            <span class="meta-value mono">{{ msg.senderId }}</span>
          </div>
          <div class="meta-row" v-if="msg.dedupeKey">
            <span class="meta-label">去重键</span>
            <span class="meta-value mono">{{ msg.dedupeKey }}</span>
          </div>
          <div class="meta-row" v-if="msg.syncTargets">
            <span class="meta-label">同步目标</span>
            <span class="meta-value">{{ msg.syncTargets }}</span>
          </div>
          <div class="meta-row" v-if="msg.syncStatus">
            <span class="meta-label">同步状态</span>
            <span class="meta-value">{{ msg.syncStatus }}</span>
          </div>
          <div class="meta-row" v-if="msg.syncedAt">
            <span class="meta-label">同步时间</span>
            <span class="meta-value">{{ formatFullTime(msg.syncedAt) }}</span>
          </div>
          <div class="meta-row" v-if="msg.processingStartedAt">
            <span class="meta-label">处理开始</span>
            <span class="meta-value">{{ formatFullTime(msg.processingStartedAt) }}</span>
          </div>
          <div class="meta-row" v-if="msg.processingAttemptCount > 0">
            <span class="meta-label">处理次数</span>
            <span class="meta-value">{{ msg.processingAttemptCount }}</span>
          </div>
        </div>

        <!-- 原始内容 -->
        <div class="detail-section">
          <h3>原始内容</h3>
          <div class="content-card">
            <p v-if="msg.contentType === 'url' && msg.rawContent" class="content-url">
              <a :href="msg.rawContent" target="_blank" rel="noopener">{{ msg.rawContent }}</a>
            </p>
            <pre v-else class="content-text">{{ msg.rawContent || '(无)' }}</pre>
          </div>
        </div>

        <!-- 标准化内容 -->
        <div class="detail-section" v-if="msg.normalizedContent && msg.normalizedContent !== msg.rawContent">
          <h3>标准化内容</h3>
          <div class="content-card">
            <pre class="content-text">{{ msg.normalizedContent }}</pre>
          </div>
        </div>

        <!-- 附件信息 -->
        <div class="detail-section" v-if="msg.attachmentsJson">
          <h3>附件信息</h3>
          <div class="content-card">
            <pre class="content-text mono">{{ formatJson(msg.attachmentsJson) }}</pre>
          </div>
        </div>

        <!-- 外部引用 -->
        <div class="detail-section" v-if="msg.externalReferencesJson">
          <h3>外部引用</h3>
          <div class="content-card">
            <pre class="content-text mono">{{ formatJson(msg.externalReferencesJson) }}</pre>
          </div>
        </div>

        <!-- 操作区 -->
        <div class="detail-actions">
          <a-space>
            <a-popconfirm
              v-if="msg.status === 'received'"
              title="确定要处理这条消息吗？将异步写入知识库。"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleProcess"
            >
              <a-button type="primary">
                <template #icon><ThunderboltOutlined /></template>
                处理
              </a-button>
            </a-popconfirm>
            <a-popconfirm
              v-if="msg.status === 'failed'"
              title="确定要重试这条消息吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleRetry"
            >
              <a-button type="primary" danger>
                <template #icon><RedoOutlined /></template>
                重试
              </a-button>
            </a-popconfirm>
            <a-popconfirm
              title="确定要删除这条消息吗？此操作不可恢复。"
              ok-text="确定删除"
              cancel-text="取消"
              ok-type="danger"
              @confirm="handleDelete"
            >
              <a-button danger>
                <template #icon><DeleteOutlined /></template>
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ThunderboltOutlined,
  RedoOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import { getMessageById, processMessage, retryMessage, deleteMessage, updateMessagePurpose } from '@/api/inbox'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const msg = ref(null)

const fetchDetail = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getMessageById(id)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      msg.value = data
    }
  } catch (err) {
    console.error('获取消息详情失败:', err)
    message.error('获取消息详情失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/inbox')
  }
}

const handleProcess = async () => {
  try {
    await processMessage(msg.value.id)
    message.success('已提交处理')
    fetchDetail()
  } catch (err) {
    message.error('处理失败')
  }
}

const handleRetry = async () => {
  try {
    await retryMessage(msg.value.id)
    message.success('已提交重试')
    fetchDetail()
  } catch (err) {
    message.error('重试失败')
  }
}

const handleDelete = async () => {
  try {
    const res = await deleteMessage(msg.value.id)
    const { errorCode } = res.data
    if (errorCode === 200) {
      message.success('已删除')
      router.push('/inbox')
    } else {
      message.error('删除失败')
    }
  } catch (err) {
    message.error('删除失败')
  }
}

const handlePurposeChange = async (value) => {
  try {
    const res = await updateMessagePurpose(msg.value.id, value || '')
    const { errorCode } = res.data
    if (errorCode === 200) {
      message.success('用途已更新')
      fetchDetail()
    } else {
      message.error('更新失败')
    }
  } catch (err) {
    message.error('更新失败')
  }
}

const formatJson = (jsonStr) => {
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

const sourceTypeColor = (type) => {
  const colors = { wechat: 'green', 'wx-official': 'green', 'web-manual': 'blue', feishu: 'purple', qq: 'orange' }
  return colors[type] || 'default'
}

const sourceTypeLabel = (type) => {
  const labels = { wechat: '微信', 'wx-official': '公众号', 'web-manual': '手动', feishu: '飞书', qq: 'QQ' }
  return labels[type] || type || '未知'
}

const contentTypeLabel = (type) => {
  const labels = { text: '文本', url: '链接', image: '图片', audio: '音频', file: '文件' }
  return labels[type] || type || '未知'
}

const statusColor = (status) => {
  const colors = { received: 'blue', processing: 'orange', parsed: 'cyan', archived: 'green', synced: 'green', failed: 'red' }
  return colors[status] || 'default'
}

const statusLabel = (status) => {
  const labels = { received: '待处理', processing: '处理中', parsed: '已解析', archived: '已归档', synced: '已同步', failed: '失败' }
  return labels[status] || status || '未知'
}

const formatFullTime = (timestamp) => {
  if (!timestamp) return ''
  const d = new Date(timestamp)
  return d.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.detail-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.detail-header h2 {
  margin: 0;
  font-size: 20px;
}

.back-btn {
  padding: 0;
}

.detail-empty {
  padding: 60px 0;
}

/* 元信息卡片 */
.detail-meta-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
}

.meta-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #fafafa;
}

.meta-row:last-child {
  border-bottom: none;
}

.meta-label {
  width: 100px;
  color: #999;
  font-size: 13px;
  flex-shrink: 0;
}

.meta-value {
  color: #333;
  font-size: 13px;
  word-break: break-all;
}

.meta-value.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  color: #666;
}

/* 内容区 */
.detail-section {
  margin-bottom: 20px;
}

.detail-section h3 {
  font-size: 15px;
  margin-bottom: 8px;
  color: #333;
}

.content-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
}

.content-url a {
  color: #1890ff;
  word-break: break-all;
  font-size: 14px;
}

.content-text {
  color: #333;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.content-text.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  color: #555;
  background: #fafafa;
  padding: 12px;
  border-radius: 4px;
}

/* 操作区 */
.detail-actions {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}
</style>
