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
      <a-space :size="12" wrap>
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索消息内容..."
          style="width: 220px"
          allow-clear
          @search="handleSearch"
          @pressEnter="handleSearch"
        />
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
        <a-range-picker
          v-model:value="dateRange"
          :placeholder="['开始日期', '结束日期']"
          style="width: 240px"
          @change="handleDateRangeChange"
        />
      </a-space>
    </div>

    <!-- 统计信息栏 + 全选 -->
    <div class="inbox-stats" v-if="total > 0">
      <div class="stats-left">
        <a-checkbox
          :checked="isAllSelected"
          :indeterminate="isPartialSelected"
          @change="toggleSelectAll"
        >
          全选
        </a-checkbox>
        <span class="stats-text">共 {{ total }} 条消息</span>
      </div>
      <div class="stats-right" v-if="autoRefreshEnabled">
        <span class="auto-refresh-hint">自动刷新中</span>
      </div>
    </div>

    <!-- 批量操作浮动栏 -->
    <transition name="slide-up">
      <div class="batch-bar" v-if="selectedIds.size > 0">
        <span class="batch-count">已选 {{ selectedIds.size }} 条</span>
        <a-space>
          <a-popconfirm
            title="确定批量处理选中的消息吗？"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleBatchProcess"
          >
            <a-button size="small" type="primary" :loading="batchLoading">
              <template #icon><ThunderboltOutlined /></template>
              批量处理
            </a-button>
          </a-popconfirm>
          <a-popconfirm
            title="确定批量删除选中的消息吗？此操作不可恢复！"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleBatchDelete"
          >
            <a-button size="small" danger :loading="batchLoading">
              <template #icon><DeleteOutlined /></template>
              批量删除
            </a-button>
          </a-popconfirm>
          <a-button size="small" @click="clearSelection">取消选择</a-button>
        </a-space>
      </div>
    </transition>

    <!-- 新消息提示条 -->
    <div class="new-msg-tip" v-if="hasNewMessages" @click="refreshList">
      有新消息到达，点击刷新
    </div>

    <!-- 消息列表 -->
    <div class="inbox-list">
      <a-spin :spinning="loading">
        <!-- 空状态引导 -->
        <div v-if="!loading && messages.length === 0" class="inbox-empty">
          <a-empty description="暂无消息">
            <a-button type="primary" @click="$router.push('/quickNote')">
              <template #icon><EditOutlined /></template>
              去快速速记
            </a-button>
          </a-empty>
        </div>

        <div v-else class="message-cards">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message-card"
            :class="{
              'message-failed': msg.status === 'failed',
              'message-selected': selectedIds.has(msg.id),
            }"
          >
            <div class="message-card-header">
              <div class="message-meta">
                <a-checkbox
                  :checked="selectedIds.has(msg.id)"
                  @change="toggleSelect(msg.id)"
                  @click.stop
                />
                <a-tag :color="sourceTypeColor(msg.sourceType)">{{ sourceTypeLabel(msg.sourceType) }}</a-tag>
                <a-tag>{{ contentTypeLabel(msg.contentType) }}</a-tag>
                <a-tag :color="statusColor(msg.status)">{{ statusLabel(msg.status) }}</a-tag>
              </div>
              <div class="message-header-right">
                <span class="message-time">{{ formatTime(msg.receivedAt) }}</span>
                <a-button type="link" size="small" class="detail-link" @click="$router.push(`/messageDetail/${msg.id}`)">
                  详情
                </a-button>
              </div>
            </div>
            <div class="message-card-body" @click="toggleExpand(msg.id)">
              <div
                class="message-content-wrap"
                :class="{ 'content-collapsed': !expandedIds.has(msg.id) && getContentLength(msg) > 200 }"
              >
                <p v-if="msg.contentType === 'url' && msg.rawContent" class="message-url">
                  <a :href="msg.rawContent" target="_blank" rel="noopener" @click.stop>{{ msg.rawContent }}</a>
                </p>
                <p class="message-content">{{ msg.normalizedContent || msg.rawContent || '' }}</p>
              </div>
              <a-button
                v-if="getContentLength(msg) > 200"
                type="link"
                size="small"
                class="expand-btn"
                @click.stop="toggleExpand(msg.id)"
              >
                {{ expandedIds.has(msg.id) ? '收起' : '展开全文' }}
              </a-button>
            </div>
            <div class="message-card-footer" v-if="msg.status === 'received' || msg.status === 'failed'">
              <a-popconfirm
                v-if="msg.status === 'received'"
                title="确定要处理这条消息吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleProcess(msg.id)"
              >
                <a-button size="small" type="link">
                  <template #icon><ThunderboltOutlined /></template>
                  处理
                </a-button>
              </a-popconfirm>
              <a-popconfirm
                v-if="msg.status === 'failed'"
                title="确定要重试这条消息吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleRetry(msg.id)"
              >
                <a-button size="small" type="link" danger>
                  <template #icon><RedoOutlined /></template>
                  重试
                </a-button>
              </a-popconfirm>
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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined,
  EditOutlined,
  ThunderboltOutlined,
  RedoOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import { getInboxMessages, processMessage, retryMessage, batchProcessMessages, batchDeleteMessages } from '@/api/inbox'

const loading = ref(false)
const batchLoading = ref(false)
const messages = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const expandedIds = ref(new Set())
const selectedIds = ref(new Set())
const hasNewMessages = ref(false)
const autoRefreshEnabled = ref(true)
const dateRange = ref(null)
let autoRefreshTimer = null
let lastTotal = 0

const filters = reactive({
  sourceType: undefined,
  status: undefined,
  contentType: undefined,
})

// 批量选择
const isAllSelected = computed(() => {
  if (messages.value.length === 0) return false
  return messages.value.every((msg) => selectedIds.value.has(msg.id))
})

const isPartialSelected = computed(() => {
  if (messages.value.length === 0) return false
  const someSelected = messages.value.some((msg) => selectedIds.value.has(msg.id))
  return someSelected && !isAllSelected.value
})

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(messages.value.map((msg) => msg.id))
  }
}

const toggleSelect = (id) => {
  const newSet = new Set(selectedIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedIds.value = newSet
}

const clearSelection = () => {
  selectedIds.value = new Set()
}

const fetchMessages = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value,
    }
    if (filters.sourceType) params.sourceType = filters.sourceType
    if (filters.status) params.status = filters.status
    if (filters.contentType) params.contentType = filters.contentType
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0].startOf('day').valueOf()
      params.endTime = dateRange.value[1].endOf('day').valueOf()
    }

    const res = await getInboxMessages(params)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      const newTotal = data.totalElements || 0
      if (silent && newTotal > lastTotal && lastTotal > 0) {
        hasNewMessages.value = true
      } else {
        messages.value = data.content || []
        total.value = newTotal
        hasNewMessages.value = false
      }
      lastTotal = newTotal
    }
  } catch (err) {
    if (!silent) {
      console.error('获取消息列表失败:', err)
      message.error('获取消息列表失败')
    }
  } finally {
    if (!silent) loading.value = false
  }
}

const refreshList = () => {
  currentPage.value = 1
  selectedIds.value = new Set()
  hasNewMessages.value = false
  fetchMessages()
}

const handleSearch = () => {
  currentPage.value = 1
  selectedIds.value = new Set()
  fetchMessages()
}

const handleFilterChange = () => {
  currentPage.value = 1
  selectedIds.value = new Set()
  fetchMessages()
}

const handleDateRangeChange = () => {
  currentPage.value = 1
  selectedIds.value = new Set()
  fetchMessages()
}

const handlePageChange = (page, size) => {
  currentPage.value = page
  pageSize.value = size
  selectedIds.value = new Set()
  fetchMessages()
}

const toggleExpand = (id) => {
  const newSet = new Set(expandedIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  expandedIds.value = newSet
}

const getContentLength = (msg) => {
  return (msg.normalizedContent || msg.rawContent || '').length
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

// 批量操作
const handleBatchProcess = async () => {
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0) return
  batchLoading.value = true
  try {
    const res = await batchProcessMessages(ids)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      message.success(`批量处理完成：成功 ${data.processed} 条，跳过 ${data.skipped} 条`)
    } else {
      message.success('已提交批量处理')
    }
    selectedIds.value = new Set()
    fetchMessages()
  } catch (err) {
    message.error('批量处理失败')
  } finally {
    batchLoading.value = false
  }
}

const handleBatchDelete = async () => {
  const ids = Array.from(selectedIds.value)
  if (ids.length === 0) return
  batchLoading.value = true
  try {
    const res = await batchDeleteMessages(ids)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      message.success(`批量删除完成：删除 ${data.deleted} 条，跳过 ${data.skipped} 条`)
    } else {
      message.success('已提交批量删除')
    }
    selectedIds.value = new Set()
    fetchMessages()
  } catch (err) {
    message.error('批量删除失败')
  } finally {
    batchLoading.value = false
  }
}

// 自动刷新（轮询）
const startAutoRefresh = () => {
  stopAutoRefresh()
  autoRefreshTimer = setInterval(() => {
    if (!loading.value && !batchLoading.value) {
      fetchMessages(true)
    }
  }, 30000)
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
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

onMounted(() => {
  fetchMessages()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
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

.inbox-stats {
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stats-text {
  color: #999;
  font-size: 13px;
}

.auto-refresh-hint {
  color: #52c41a;
  font-size: 12px;
}

.auto-refresh-hint::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #52c41a;
  margin-right: 4px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* 批量操作栏 */
.batch-bar {
  position: sticky;
  bottom: 16px;
  z-index: 10;
  background: #fff;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
}

.batch-count {
  font-weight: 600;
  color: #1890ff;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.2s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* 新消息提示条 */
.new-msg-tip {
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 6px;
  padding: 8px 16px;
  margin-bottom: 12px;
  text-align: center;
  color: #1890ff;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.2s;
}

.new-msg-tip:hover {
  background: #bae7ff;
}

.inbox-list {
  min-height: 200px;
}

.inbox-empty {
  padding: 40px 0;
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
  transition: box-shadow 0.2s, border-color 0.2s;
}

.message-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.message-card.message-failed {
  border-left: 3px solid #ff4d4f;
}

.message-card.message-selected {
  border-color: #1890ff;
  background: #f0f7ff;
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
  flex-wrap: wrap;
  align-items: center;
}

.message-time {
  color: #999;
  font-size: 13px;
  white-space: nowrap;
}

.message-header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.detail-link {
  padding: 0;
  height: auto;
  font-size: 12px;
  color: #999;
}

.detail-link:hover {
  color: #1890ff;
}

.message-card-body {
  cursor: pointer;
}

.message-content-wrap {
  overflow: hidden;
  position: relative;
}

.message-content-wrap.content-collapsed {
  max-height: 100px;
}

.message-content-wrap.content-collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 30px;
  background: linear-gradient(transparent, #fff);
}

.message-selected .message-content-wrap.content-collapsed::after {
  background: linear-gradient(transparent, #f0f7ff);
}

.message-url {
  margin: 0 0 4px 0;
}

.message-url a {
  color: #1890ff;
  word-break: break-all;
}

.message-content {
  color: #333;
  line-height: 1.6;
  margin: 0;
  word-break: break-all;
  white-space: pre-wrap;
}

.expand-btn {
  padding: 0;
  height: auto;
  font-size: 12px;
}

.message-card-footer {
  margin-top: 8px;
  text-align: right;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.inbox-pagination {
  margin-top: 20px;
  text-align: center;
}
</style>
