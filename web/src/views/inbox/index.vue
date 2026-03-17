<template>
  <div class="inbox-container">
    <div class="inbox-header">
      <h2>消息收件箱</h2>
      <div class="inbox-actions">
        <a-button type="primary" @click="refreshList">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="inbox-filters">
      <a-space :size="12">
        <a-select
          v-model:value="filters.sourceType"
          placeholder="来源类型"
          allowClear
          style="width: 140px"
          @change="handleFilterChange"
        >
          <a-select-option value="wechat">微信</a-select-option>
          <a-select-option value="wx-official">公众号</a-select-option>
          <a-select-option value="web-manual">手动输入</a-select-option>
          <a-select-option value="feishu">飞书</a-select-option>
        </a-select>
        <a-select
          v-model:value="filters.status"
          placeholder="处理状态"
          allowClear
          style="width: 140px"
          @change="handleFilterChange"
        >
          <a-select-option value="received">待处理</a-select-option>
          <a-select-option value="processing">处理中</a-select-option>
          <a-select-option value="parsed">已解析</a-select-option>
          <a-select-option value="archived">已归档</a-select-option>
          <a-select-option value="synced">已同步</a-select-option>
          <a-select-option value="failed">失败</a-select-option>
        </a-select>
        <a-select
          v-model:value="filters.contentType"
          placeholder="内容类型"
          allowClear
          style="width: 140px"
          @change="handleFilterChange"
        >
          <a-select-option value="text">文本</a-select-option>
          <a-select-option value="url">链接</a-select-option>
          <a-select-option value="image">图片</a-select-option>
          <a-select-option value="audio">音频</a-select-option>
          <a-select-option value="file">文件</a-select-option>
        </a-select>
      </a-space>
    </div>

    <!-- 消息列表 -->
    <div class="inbox-list">
      <a-spin :spinning="loading">
        <a-empty v-if="!loading && messages.length === 0" description="暂无消息" />
        <div v-else class="message-cards">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message-card"
            :class="{ 'message-failed': msg.status === 'failed' }"
          >
            <div class="message-card-header">
              <div class="message-meta">
                <a-tag :color="sourceTypeColor(msg.sourceType)">{{ sourceTypeLabel(msg.sourceType) }}</a-tag>
                <a-tag>{{ msg.contentType }}</a-tag>
                <a-tag :color="statusColor(msg.status)">{{ statusLabel(msg.status) }}</a-tag>
              </div>
              <span class="message-time">{{ formatTime(msg.receivedAt) }}</span>
            </div>
            <div class="message-card-body">
              <p class="message-content">{{ displayContent(msg) }}</p>
            </div>
            <div class="message-card-footer" v-if="msg.status === 'received' || msg.status === 'failed'">
              <a-button
                v-if="msg.status === 'received'"
                size="small"
                type="link"
                @click="handleProcess(msg.id)"
              >处理</a-button>
              <a-button
                v-if="msg.status === 'failed'"
                size="small"
                type="link"
                danger
                @click="handleRetry(msg.id)"
              >重试</a-button>
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <!-- 分页 -->
    <div class="inbox-pagination" v-if="total > 0">
      <a-pagination
        v-model:current="currentPage"
        v-model:pageSize="pageSize"
        :total="total"
        show-size-changer
        :page-size-options="['10', '20', '50']"
        @change="handlePageChange"
        @showSizeChange="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { getInboxMessages, processMessage, retryMessage } from '@/api/inbox'

const loading = ref(false)
const messages = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filters = reactive({
  sourceType: undefined,
  status: undefined,
  contentType: undefined,
})

const fetchMessages = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1, // 后端从0开始
      size: pageSize.value,
    }
    if (filters.sourceType) params.sourceType = filters.sourceType
    if (filters.status) params.status = filters.status
    if (filters.contentType) params.contentType = filters.contentType

    const res = await getInboxMessages(params)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      messages.value = data.content || []
      total.value = data.totalElements || 0
    }
  } catch (err) {
    console.error('获取消息列表失败:', err)
    message.error('获取消息列表失败')
  } finally {
    loading.value = false
  }
}

const refreshList = () => {
  currentPage.value = 1
  fetchMessages()
}

const handleFilterChange = () => {
  currentPage.value = 1
  fetchMessages()
}

const handlePageChange = (page, size) => {
  currentPage.value = page
  pageSize.value = size
  fetchMessages()
}

const handleProcess = async (id) => {
  try {
    await processMessage(id)
    message.success('已提交处理')
    fetchMessages()
  } catch (err) {
    message.error('处理失败')
  }
}

const handleRetry = async (id) => {
  try {
    await retryMessage(id)
    message.success('已提交重试')
    fetchMessages()
  } catch (err) {
    message.error('重试失败')
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

const statusColor = (status) => {
  const colors = { received: 'blue', processing: 'orange', parsed: 'cyan', archived: 'green', synced: 'green', failed: 'red' }
  return colors[status] || 'default'
}

const statusLabel = (status) => {
  const labels = { received: '待处理', processing: '处理中', parsed: '已解析', archived: '已归档', synced: '已同步', failed: '失败' }
  return labels[status] || status || '未知'
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const d = new Date(timestamp)
  const now = new Date()
  const diffMs = now - d
  if (diffMs < 60000) return '刚刚'
  if (diffMs < 3600000) return Math.floor(diffMs / 60000) + '分钟前'
  if (diffMs < 86400000) return Math.floor(diffMs / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const displayContent = (msg) => {
  const content = msg.normalizedContent || msg.rawContent || ''
  return content.length > 200 ? content.substring(0, 200) + '...' : content
}

onMounted(() => {
  fetchMessages()
})
</script>

<style scoped>
.inbox-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.inbox-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.inbox-header h2 {
  margin: 0;
  font-size: 20px;
}

.inbox-filters {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 6px;
}

.inbox-list {
  min-height: 200px;
}

.message-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.message-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.message-card.message-failed {
  border-left: 3px solid #ff4d4f;
}

.message-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.message-meta {
  display: flex;
  gap: 4px;
}

.message-time {
  color: #999;
  font-size: 13px;
}

.message-content {
  color: #333;
  line-height: 1.6;
  margin: 0;
  word-break: break-all;
  white-space: pre-wrap;
}

.message-card-footer {
  margin-top: 8px;
  text-align: right;
}

.inbox-pagination {
  margin-top: 20px;
  text-align: center;
}
</style>
