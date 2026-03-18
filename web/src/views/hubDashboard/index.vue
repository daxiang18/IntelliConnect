<template>
  <div class="hub-dashboard">
    <div class="dashboard-header">
      <h2>个人中枢</h2>
      <span class="dashboard-greeting">{{ greeting }}</span>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: #e6f7ff">
          <InboxOutlined style="color: #1890ff; font-size: 24px" />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.total || 0 }}</span>
          <span class="stat-label">消息总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fff7e6">
          <ClockCircleOutlined style="color: #fa8c16; font-size: 24px" />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.byStatus?.received || 0 }}</span>
          <span class="stat-label">待处理</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #f6ffed">
          <CheckCircleOutlined style="color: #52c41a; font-size: 24px" />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ archivedCount }}</span>
          <span class="stat-label">已归档</span>
        </div>
      </div>
      <div class="stat-card" @click="$router.push('/todoList')" style="cursor: pointer">
        <div class="stat-icon" style="background: #fff0f6">
          <OrderedListOutlined style="color: #eb2f96; font-size: 24px" />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ pendingTodoCount }}</span>
          <span class="stat-label">待办事项</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fff1f0">
          <WarningOutlined style="color: #ff4d4f; font-size: 24px" />
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.byStatus?.failed || 0 }}</span>
          <span class="stat-label">失败</span>
        </div>
      </div>
    </div>

    <!-- 来源分布 -->
    <div class="dashboard-section" v-if="sourcePairs.length > 0">
      <h3>来源分布</h3>
      <div class="source-bars">
        <div v-for="item in sourcePairs" :key="item.key" class="source-bar-item">
          <div class="source-bar-label">
            <a-tag :color="sourceTypeColor(item.key)" size="small">{{ sourceTypeLabel(item.key) }}</a-tag>
            <span class="source-count">{{ item.count }}</span>
          </div>
          <a-progress
            :percent="Math.round((item.count / stats.total) * 100)"
            :stroke-color="sourceBarColor(item.key)"
            :show-info="false"
            size="small"
          />
        </div>
      </div>
    </div>

    <!-- 文档用途分布 -->
    <div class="dashboard-section" v-if="purposePairs.length > 0">
      <h3>文档用途分布</h3>
      <div class="source-bars">
        <div v-for="item in purposePairs" :key="item.key" class="source-bar-item">
          <div class="source-bar-label">
            <a-tag :color="purposeColor(item.key)" size="small">{{ purposeLabel(item.key) }}</a-tag>
            <span class="source-count">{{ item.count }}</span>
          </div>
          <a-progress
            :percent="purposeTotal > 0 ? Math.round((item.count / purposeTotal) * 100) : 0"
            :stroke-color="purposeBarColor(item.key)"
            :show-info="false"
            size="small"
          />
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="dashboard-section">
      <h3>快捷入口</h3>
      <div class="quick-links">
        <div class="quick-link-card" @click="$router.push('/inbox')">
          <InboxOutlined style="font-size: 28px; color: #1890ff" />
          <span>消息收件箱</span>
          <span class="quick-link-badge" v-if="stats.byStatus?.received">{{ stats.byStatus.received }}</span>
        </div>
        <div class="quick-link-card" @click="$router.push('/quickNote')">
          <EditOutlined style="font-size: 28px; color: #722ed1" />
          <span>快速速记</span>
        </div>
        <div class="quick-link-card" @click="$router.push('/archive')">
          <FolderOpenOutlined style="font-size: 28px; color: #52c41a" />
          <span>知识归档</span>
          <span class="quick-link-badge success" v-if="archivedCount">{{ archivedCount }}</span>
        </div>
        <div class="quick-link-card" @click="$router.push('/todoList')">
          <OrderedListOutlined style="font-size: 28px; color: #eb2f96" />
          <span>待办事项</span>
          <span class="quick-link-badge" v-if="pendingTodoCount">{{ pendingTodoCount }}</span>
        </div>
      </div>
    </div>

    <!-- 待办事项快览 -->
    <div class="dashboard-section" v-if="pendingTodos.length > 0">
      <div class="section-header">
        <h3>待办事项</h3>
        <a-button type="link" @click="$router.push('/todoList')">查看全部</a-button>
      </div>
      <div class="todo-quick-list">
        <div v-for="todo in pendingTodos" :key="todo.id" class="todo-quick-item">
          <div class="todo-quick-left">
            <a-tag :color="todoPriorityColor(todo.priority)" size="small">{{ todoPriorityLabel(todo.priority) }}</a-tag>
            <span class="todo-quick-content">{{ todo.content }}</span>
          </div>
          <span class="todo-quick-time">{{ formatTime(todo.createdAt) }}</span>
        </div>
      </div>
    </div>

    <!-- 最近消息 -->
    <div class="dashboard-section">
      <div class="section-header">
        <h3>最近消息</h3>
        <a-button type="link" @click="$router.push('/inbox')">查看全部</a-button>
      </div>
      <a-spin :spinning="loadingRecent">
        <a-empty v-if="!loadingRecent && recentMessages.length === 0" description="暂无消息" />
        <div v-else class="recent-list">
          <div v-for="msg in recentMessages" :key="msg.id" class="recent-item">
            <div class="recent-item-left">
              <a-tag :color="sourceTypeColor(msg.sourceType)" size="small">
                {{ sourceTypeLabel(msg.sourceType) }}
              </a-tag>
              <span class="recent-content">{{ truncate(msg.normalizedContent || msg.rawContent || '', 80) }}</span>
            </div>
            <div class="recent-item-right">
              <a-tag :color="statusColor(msg.status)" size="small">{{ statusLabel(msg.status) }}</a-tag>
              <span class="recent-time">{{ formatTime(msg.receivedAt) }}</span>
            </div>
          </div>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  InboxOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  EditOutlined,
  FolderOpenOutlined,
  OrderedListOutlined,
} from '@ant-design/icons-vue'
import { getMessageStats, getInboxMessages } from '@/api/inbox'
import { getTodos } from '@/api/todo'

const stats = ref({})
const recentMessages = ref([])
const loadingRecent = ref(false)
const pendingTodos = ref([])
const pendingTodoCount = ref(0)

const archivedCount = computed(() => {
  const s = stats.value.byStatus || {}
  return (s.archived || 0) + (s.synced || 0) + (s.parsed || 0) + (s.ingested || 0)
})

const sourcePairs = computed(() => {
  const bySource = stats.value.bySource || {}
  return Object.entries(bySource)
    .map(([key, count]) => ({ key, count }))
    .sort((a, b) => b.count - a.count)
})

const purposePairs = computed(() => {
  const byPurpose = stats.value.byPurpose || {}
  return Object.entries(byPurpose)
    .map(([key, count]) => ({ key, count }))
    .sort((a, b) => b.count - a.count)
})

const purposeTotal = computed(() => {
  return purposePairs.value.reduce((sum, item) => sum + item.count, 0)
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了，注意休息'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const fetchStats = async () => {
  try {
    const res = await getMessageStats()
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      stats.value = data
    }
  } catch (err) {
    console.error('获取统计数据失败:', err)
  }
}

const fetchRecent = async () => {
  loadingRecent.value = true
  try {
    const res = await getInboxMessages({ page: 0, size: 8 })
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      recentMessages.value = data.content || []
    }
  } catch (err) {
    console.error('获取最近消息失败:', err)
  } finally {
    loadingRecent.value = false
  }
}

const fetchPendingTodos = async () => {
  try {
    const res = await getTodos({ page: 0, size: 5, status: 'pending' })
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      pendingTodos.value = data.content || []
      pendingTodoCount.value = data.totalElements || 0
    }
  } catch (err) {
    console.error('获取待办失败:', err)
  }
}

const todoPriorityColor = (p) => ({ high: 'red', medium: 'orange', low: 'blue' }[p] || 'default')
const todoPriorityLabel = (p) => ({ high: '紧急', medium: '一般', low: '可选' }[p] || p)

const truncate = (text, len) => {
  return text.length > len ? text.substring(0, len) + '...' : text
}

const sourceTypeColor = (type) => {
  const colors = { wechat: 'green', 'wx-official': 'green', 'web-manual': 'blue', feishu: 'purple', qq: 'orange' }
  return colors[type] || 'default'
}

const sourceTypeLabel = (type) => {
  const labels = { wechat: '微信', 'wx-official': '公众号', 'web-manual': '手动', feishu: '飞书', qq: 'QQ' }
  return labels[type] || type || '未知'
}

const sourceBarColor = (type) => {
  const colors = { wechat: '#52c41a', 'wx-official': '#52c41a', 'web-manual': '#1890ff', feishu: '#722ed1', qq: '#fa8c16' }
  return colors[type] || '#d9d9d9'
}

const purposeColor = (purpose) => {
  const colors = { quick_capture: 'gold', study_doc: 'purple', work_doc: 'geekblue', life_record: 'cyan' }
  return colors[purpose] || 'default'
}

const purposeLabel = (purpose) => {
  const labels = { quick_capture: '速记', study_doc: '学习', work_doc: '工作', life_record: '生活' }
  return labels[purpose] || purpose || '未知'
}

const purposeBarColor = (purpose) => {
  const colors = { quick_capture: '#faad14', study_doc: '#722ed1', work_doc: '#2f54eb', life_record: '#13c2c2' }
  return colors[purpose] || '#d9d9d9'
}

const statusColor = (status) => {
  const colors = { received: 'blue', processing: 'orange', parsed: 'cyan', archived: 'green', synced: 'green', ingested: 'green', failed: 'red' }
  return colors[status] || 'default'
}

const statusLabel = (status) => {
  const labels = { received: '待处理', processing: '处理中', parsed: '已解析', archived: '已归档', synced: '已同步', ingested: '已入库', failed: '失败' }
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
  fetchStats()
  fetchRecent()
  fetchPendingTodos()
})
</script>

<style scoped>
.hub-dashboard {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 24px;
}

.dashboard-header h2 {
  margin: 0;
  font-size: 22px;
}

.dashboard-greeting {
  color: #999;
  font-size: 14px;
}

/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: box-shadow 0.2s;
}

.stat-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: #333;
}

.stat-label {
  font-size: 13px;
  color: #999;
  margin-top: 2px;
}

/* 通用 section */
.dashboard-section {
  margin-bottom: 24px;
}

.dashboard-section h3 {
  font-size: 16px;
  margin-bottom: 12px;
  color: #333;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin-bottom: 0;
}

/* 来源分布 */
.source-bars {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
}

.source-bar-item {
  margin-bottom: 12px;
}

.source-bar-item:last-child {
  margin-bottom: 0;
}

.source-bar-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.source-count {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

/* 快捷入口 */
.quick-links {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.quick-link-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.quick-link-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border-color: #d9d9d9;
}

.quick-link-card span:not(.quick-link-badge) {
  font-size: 14px;
  color: #333;
}

.quick-link-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

.quick-link-badge.success {
  background: #52c41a;
}

/* 待办快览 */
.todo-quick-list {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.todo-quick-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #fafafa;
  gap: 12px;
}

.todo-quick-item:last-child {
  border-bottom: none;
}

.todo-quick-item:hover {
  background: #fafafa;
}

.todo-quick-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.todo-quick-content {
  color: #333;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.todo-quick-time {
  color: #999;
  font-size: 12px;
  white-space: nowrap;
}

/* 最近消息 */
.recent-list {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #fafafa;
  gap: 12px;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-item:hover {
  background: #fafafa;
}

.recent-item-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.recent-content {
  color: #333;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-item-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.recent-time {
  color: #999;
  font-size: 12px;
  white-space: nowrap;
}

/* 响应式 */
@media (max-width: 768px) {
  .hub-dashboard {
    padding: 12px;
  }

  .dashboard-header h2 {
    font-size: 18px;
  }

  .stat-cards {
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }

  .stat-card {
    padding: 12px;
  }

  .stat-icon {
    width: 40px;
    height: 40px;
  }

  .stat-value {
    font-size: 20px;
  }

  .quick-links {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }

  .quick-link-card {
    padding: 14px;
  }

  .recent-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .recent-item-right {
    align-self: flex-end;
  }
}

@media (max-width: 480px) {
  .hub-dashboard {
    padding: 8px;
  }

  .stat-cards {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  .stat-card {
    padding: 10px;
    gap: 8px;
  }

  .stat-icon {
    width: 36px;
    height: 36px;
  }

  .stat-value {
    font-size: 18px;
  }

  .stat-label {
    font-size: 12px;
  }

  .quick-links {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  .quick-link-card {
    padding: 12px;
    gap: 6px;
  }
}
</style>
