<template>
  <div class="inbox-layout">
    <!-- 分类侧边栏 -->
    <div class="category-sidebar" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <div class="sidebar-header">
        <h3 v-if="!sidebarCollapsed">分类浏览</h3>
        <a-button type="text" size="small" @click="sidebarCollapsed = !sidebarCollapsed">
          <template #icon>
            <MenuFoldOutlined v-if="!sidebarCollapsed" />
            <MenuUnfoldOutlined v-else />
          </template>
        </a-button>
      </div>

      <template v-if="!sidebarCollapsed">
        <!-- 分类列表 -->
        <div class="category-list">
          <div
            class="category-item"
            :class="{ active: !filters.contentCategory }"
            @click="selectCategory(null)"
          >
            <span class="category-name">全部分类</span>
            <span class="category-count">{{ total }}</span>
          </div>
          <div
            v-for="cat in categoryList"
            :key="cat.category"
            class="category-item"
            :class="{ active: filters.contentCategory === cat.category }"
            @click="selectCategory(cat.category)"
          >
            <span class="category-icon">{{ categoryIcon(cat.category) }}</span>
            <span class="category-name">{{ categoryLabel(cat.category) }}</span>
            <span class="category-count">{{ cat.count }}</span>
          </div>
        </div>

        <!-- 标签云 -->
        <div class="tag-cloud-section" v-if="tagList.length > 0">
          <h4>标签筛选</h4>
          <div class="tag-cloud">
            <a-tag
              v-for="tagItem in tagList"
              :key="tagItem.tag"
              :color="filters.contentTag === tagItem.tag ? 'blue' : 'default'"
              class="tag-cloud-item"
              @click="selectTag(tagItem.tag)"
            >
              {{ tagItem.tag }}
              <span class="tag-count-badge">{{ tagItem.count }}</span>
            </a-tag>
          </div>
          <a-button
            v-if="filters.contentTag"
            type="link"
            size="small"
            @click="selectTag(null)"
            class="clear-tag-btn"
          >
            清除标签筛选
          </a-button>
        </div>
      </template>
    </div>

    <!-- 主内容区 -->
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

      <!-- 当前筛选条件提示 -->
      <div class="active-filters" v-if="filters.contentCategory || filters.contentTag">
        <span class="filter-label">当前筛选：</span>
        <a-tag v-if="filters.contentCategory" closable @close="selectCategory(null)" color="blue">
          分类: {{ categoryLabel(filters.contentCategory) }}
        </a-tag>
        <a-tag v-if="filters.contentTag" closable @close="selectTag(null)" color="purple">
          标签: {{ filters.contentTag }}
        </a-tag>
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
            <a-select-option value="ingested">已入库</a-select-option>
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
          <a-select
            v-model:value="filters.documentPurpose"
            placeholder="文档用途"
            allowClear
            style="width: 140px"
            @change="handleFilterChange"
          >
            <a-select-option value="quick_capture">速记</a-select-option>
            <a-select-option value="study_doc">学习文档</a-select-option>
            <a-select-option value="work_doc">工作文档</a-select-option>
            <a-select-option value="life_record">生活记录</a-select-option>
          </a-select>
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
                  <a-tag v-if="msg.documentPurpose" :color="purposeColor(msg.documentPurpose)" size="small">
                    {{ purposeLabel(msg.documentPurpose) }}
                  </a-tag>
                  <template v-if="msg.category">
                    <a-tag
                      size="small"
                      color="default"
                      class="clickable-tag"
                      @click.stop="selectCategory(msg.category)"
                    >{{ msg.category }}</a-tag>
                  </template>
                  <template v-if="msg.tags && msg.tags.length > 0">
                    <a-tag
                      v-for="tag in msg.tags"
                      :key="tag"
                      size="small"
                      color="default"
                      class="content-tag clickable-tag"
                      @click.stop="selectTag(tag)"
                    >{{ tag }}</a-tag>
                  </template>
                </div>
                <div class="message-header-right">
                  <span class="message-time">{{ formatTime(msg.receivedAt) }}</span>
                  <a-button type="link" size="small" class="detail-link" @click="$router.push(`/messageDetail/${msg.id}`)">
                    详情
                  </a-button>
                </div>
              </div>
              <div class="message-card-body" @click="toggleExpand(msg.id)">
                <p v-if="msg.aiSummary" class="message-ai-summary">
                  <span class="ai-label">AI</span>
                  {{ msg.aiSummary }}
                </p>
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
              <div class="message-card-footer" v-if="msg.status === 'received' || msg.status === 'failed' || msg.status === 'ingested'">
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
                <a-popconfirm
                  v-if="msg.status === 'ingested'"
                  title="重新进行 AI 分析（分类/摘要/待办提取）？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handleReprocess(msg.id)"
                >
                  <a-button size="small" type="link" style="color: #722ed1;">
                    <template #icon><SyncOutlined /></template>
                    重新分析
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
  SyncOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons-vue'
import { getInboxMessages, processMessage, retryMessage, reprocessMessage, batchProcessMessages, batchDeleteMessages, getCategoryStats } from '@/api/inbox'

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
const sidebarCollapsed = ref(false)
const categoryList = ref([])
const tagList = ref([])
let autoRefreshTimer = null
let lastTotal = 0

const filters = reactive({
  sourceType: undefined,
  status: undefined,
  contentType: undefined,
  documentPurpose: undefined,
  contentCategory: undefined,
  contentTag: undefined,
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

// 分类侧边栏数据加载
const fetchCategoryStats = async () => {
  try {
    const res = await getCategoryStats()
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      categoryList.value = data.categories || []
      tagList.value = data.tags || []
    }
  } catch (err) {
    console.error('获取分类统计失败:', err)
  }
}

const selectCategory = (category) => {
  filters.contentCategory = category || undefined
  currentPage.value = 1
  selectedIds.value = new Set()
  fetchMessages()
}

const selectTag = (tag) => {
  filters.contentTag = tag || undefined
  currentPage.value = 1
  selectedIds.value = new Set()
  fetchMessages()
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
    if (filters.documentPurpose) params.documentPurpose = filters.documentPurpose
    if (filters.contentCategory) params.contentCategory = filters.contentCategory
    if (filters.contentTag) params.contentTag = filters.contentTag
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
  fetchCategoryStats()
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

const handleReprocess = async (id) => {
  try {
    await reprocessMessage(id)
    message.success('已提交重新分析，请稍候刷新查看结果')
    // 延迟刷新，给后端异步处理一点时间
    setTimeout(() => {
      fetchMessages()
      fetchCategoryStats()
    }, 3000)
  } catch (err) {
    message.error('重新分析失败')
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
    fetchCategoryStats()
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
    fetchCategoryStats()
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

// 分类相关 helpers
const categoryIcon = (category) => {
  const icons = {
    task: '\u2705', meeting: '\uD83D\uDCC5', question: '\u2753', idea: '\uD83D\uDCA1',
    knowledge: '\uD83D\uDCD6', reference: '\uD83D\uDD17', note: '\uD83D\uDCDD', schedule: '\u23F0',
  }
  return icons[category] || '\uD83D\uDCC4'
}

const categoryLabel = (category) => {
  const labels = {
    task: '任务', meeting: '会议', question: '问题', idea: '灵感',
    knowledge: '知识', reference: '参考', note: '笔记', schedule: '日程',
  }
  return labels[category] || category || '未分类'
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
  const colors = { received: 'blue', processing: 'orange', parsed: 'cyan', archived: 'green', synced: 'green', ingested: 'green', failed: 'red' }
  return colors[status] || 'default'
}

const statusLabel = (status) => {
  const labels = { received: '待处理', processing: '处理中', parsed: '已解析', archived: '已归档', synced: '已同步', ingested: '已入库', failed: '失败' }
  return labels[status] || status || '未知'
}

const purposeColor = (purpose) => {
  const colors = { quick_capture: 'gold', study_doc: 'purple', work_doc: 'geekblue', life_record: 'cyan' }
  return colors[purpose] || 'default'
}

const purposeLabel = (purpose) => {
  const labels = { quick_capture: '速记', study_doc: '学习', work_doc: '工作', life_record: '生活' }
  return labels[purpose] || purpose || ''
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
  fetchCategoryStats()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
/* 整体布局：侧边栏 + 主内容 */
.inbox-layout {
  display: flex;
  gap: 0;
  min-height: calc(100vh - 64px);
}

/* 分类侧边栏 */
.category-sidebar {
  width: 220px;
  min-width: 220px;
  background: #fff;
  border-right: 1px solid #f0f0f0;
  padding: 16px 0;
  overflow-y: auto;
  transition: width 0.2s, min-width 0.2s, padding 0.2s;
}

.category-sidebar.sidebar-collapsed {
  width: 48px;
  min-width: 48px;
  padding: 16px 4px;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 12px 12px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 8px;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

/* 分类列表 */
.category-list {
  padding: 4px 0;
}

.category-item {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  cursor: pointer;
  transition: background 0.15s;
  font-size: 13px;
  color: #555;
}

.category-item:hover {
  background: #f5f5f5;
}

.category-item.active {
  background: #e6f7ff;
  color: #1890ff;
  font-weight: 500;
}

.category-icon {
  margin-right: 8px;
  font-size: 15px;
}

.category-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-count {
  margin-left: 8px;
  font-size: 12px;
  color: #999;
  background: #f0f0f0;
  padding: 0 6px;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

.category-item.active .category-count {
  background: #bae7ff;
  color: #1890ff;
}

/* 标签云 */
.tag-cloud-section {
  padding: 12px 12px 0;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.tag-cloud-section h4 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-cloud-item {
  cursor: pointer;
  font-size: 12px;
  margin: 0;
}

.tag-cloud-item:hover {
  opacity: 0.8;
}

.tag-count-badge {
  margin-left: 2px;
  font-size: 10px;
  color: #999;
}

.clear-tag-btn {
  padding: 0;
  height: auto;
  font-size: 12px;
  margin-top: 6px;
}

/* 主内容区 */
.inbox-container {
  flex: 1;
  padding: 20px;
  max-width: 900px;
  min-width: 0;
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

/* 当前筛选提示 */
.active-filters {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f0f7ff;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: #666;
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

.clickable-tag {
  cursor: pointer;
}

.clickable-tag:hover {
  opacity: 0.7;
}

.message-card-body {
  cursor: pointer;
}

.message-ai-summary {
  color: #666;
  font-size: 12px;
  font-style: italic;
  margin: 0 0 6px 0;
  padding: 4px 8px;
  background: #f5f7fa;
  border-radius: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ai-label {
  display: inline-block;
  background: linear-gradient(135deg, #597ef7, #9254de);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
  padding: 0 5px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: middle;
  line-height: 16px;
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

/* 移动端适配 */
@media (max-width: 768px) {
  .inbox-layout {
    flex-direction: column;
  }

  .category-sidebar {
    width: 100% !important;
    min-width: 0 !important;
    border-right: none;
    border-bottom: 1px solid #f0f0f0;
    max-height: none;
    padding: 12px 0;
  }

  .category-sidebar.sidebar-collapsed {
    width: 100% !important;
    min-width: 0 !important;
    padding: 8px 4px;
  }

  .category-list {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    padding: 4px 12px;
  }

  .category-item {
    padding: 4px 10px;
    border-radius: 16px;
    background: #f5f5f5;
    font-size: 12px;
  }

  .category-item.active {
    background: #e6f7ff;
  }

  .tag-cloud-section {
    padding: 8px 12px 0;
  }

  .inbox-container {
    padding: 12px;
  }

  .inbox-header h2 {
    font-size: 17px;
  }

  .inbox-filters {
    padding: 10px 12px;
  }

  .inbox-filters :deep(.ant-space) {
    gap: 8px !important;
  }

  .inbox-filters :deep(.ant-input-search),
  .inbox-filters :deep(.ant-select),
  .inbox-filters :deep(.ant-picker) {
    width: 100% !important;
    min-width: 0;
  }

  .message-card {
    padding: 12px;
  }

  .message-card-header {
    flex-wrap: wrap;
    gap: 6px;
  }

  .message-meta {
    gap: 2px;
  }

  .message-meta :deep(.ant-tag) {
    font-size: 11px;
    padding: 0 4px;
    margin-inline-end: 2px;
  }

  .message-header-right {
    margin-left: auto;
  }

  .batch-bar {
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px 12px;
  }

  .batch-count {
    font-size: 13px;
  }

  .inbox-pagination :deep(.ant-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }

  .inbox-pagination :deep(.ant-pagination-options) {
    display: none;
  }
}

@media (max-width: 480px) {
  .inbox-container {
    padding: 8px;
  }

  .inbox-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .message-content {
    font-size: 14px;
  }

  .message-card-footer {
    flex-wrap: wrap;
  }
}
</style>
