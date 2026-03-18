<template>
  <div class="todo-container">
    <div class="todo-header">
      <h2>待办事项</h2>
      <a-button type="primary" @click="refreshAll">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <!-- 统计卡片 -->
    <a-row :gutter="[16, 16]" class="todo-stats">
      <a-col :xs="8" :sm="8">
        <a-card class="stat-card stat-pending" :bordered="false">
          <div class="stat-value">{{ pendingCount }}</div>
          <div class="stat-label">待处理</div>
        </a-card>
      </a-col>
      <a-col :xs="8" :sm="8">
        <a-card class="stat-card stat-completed" :bordered="false">
          <div class="stat-value">{{ completedCount }}</div>
          <div class="stat-label">已完成</div>
        </a-card>
      </a-col>
      <a-col :xs="8" :sm="8">
        <a-card class="stat-card stat-total" :bordered="false">
          <div class="stat-value">{{ total }}</div>
          <div class="stat-label">全部</div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 筛选栏 -->
    <div class="todo-filters">
      <a-space :size="12" wrap>
        <a-select
          v-model:value="filterStatus"
          placeholder="状态筛选"
          allowClear
          style="width: 140px"
          @change="handleFilterChange"
        >
          <a-select-option value="pending">待处理</a-select-option>
          <a-select-option value="completed">已完成</a-select-option>
          <a-select-option value="cancelled">已取消</a-select-option>
        </a-select>
      </a-space>
    </div>

    <!-- 待办列表 -->
    <div class="todo-list">
      <a-spin :spinning="loading">
        <a-empty v-if="!loading && todos.length === 0" description="暂无待办事项" />
        <div v-else class="todo-cards">
          <div
            v-for="todo in todos"
            :key="todo.id"
            class="todo-card"
            :class="{
              'todo-completed': todo.status === 'completed',
              'todo-cancelled': todo.status === 'cancelled',
              'todo-high': todo.priority === 'high',
            }"
          >
            <div class="todo-card-header">
              <div class="todo-card-meta">
                <a-tag :color="priorityColor(todo.priority)" size="small">
                  {{ priorityLabel(todo.priority) }}
                </a-tag>
                <a-tag :color="statusColor(todo.status)" size="small">
                  {{ statusLabel(todo.status) }}
                </a-tag>
              </div>
              <span class="todo-time">{{ formatTime(todo.createdAt) }}</span>
            </div>

            <div class="todo-card-body">
              <p class="todo-content" :class="{ 'line-through': todo.status === 'completed' }">
                {{ todo.content }}
              </p>
            </div>

            <div class="todo-card-footer">
              <a-button
                v-if="todo.status === 'pending'"
                type="link"
                size="small"
                @click="handleComplete(todo.id)"
              >
                <template #icon><CheckOutlined /></template>
                完成
              </a-button>
              <a-button
                v-if="todo.status === 'pending'"
                type="link"
                size="small"
                @click="handleCancel(todo.id)"
              >
                <template #icon><CloseOutlined /></template>
                取消
              </a-button>
              <a-button
                v-if="todo.status !== 'pending'"
                type="link"
                size="small"
                @click="handleReopen(todo.id)"
              >
                <template #icon><UndoOutlined /></template>
                重新打开
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="$router.push(`/messageDetail/${todo.messageId}`)"
              >
                来源消息
              </a-button>
              <a-popconfirm title="确定删除此待办？" @confirm="handleDelete(todo.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <!-- 分页 -->
    <div class="todo-pagination" v-if="total > 0">
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
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined,
  CheckOutlined,
  CloseOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue'
import { getTodos, updateTodoStatus, deleteTodo } from '@/api/todo'

const loading = ref(false)
const todos = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterStatus = ref(undefined)

// 简易统计（从当前页数据和 total 推断）
const pendingCount = computed(() => todos.value.filter((t) => t.status === 'pending').length)
const completedCount = computed(() => todos.value.filter((t) => t.status === 'completed').length)

const fetchTodos = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value,
    }
    if (filterStatus.value) params.status = filterStatus.value

    const res = await getTodos(params)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      todos.value = data.content || []
      total.value = data.totalElements || 0
    }
  } catch (err) {
    console.error('获取待办失败:', err)
    message.error('获取待办列表失败')
  } finally {
    loading.value = false
  }
}

const refreshAll = () => {
  currentPage.value = 1
  fetchTodos()
}

const handleFilterChange = () => {
  currentPage.value = 1
  fetchTodos()
}

const handlePageChange = (page, size) => {
  currentPage.value = page
  pageSize.value = size
  fetchTodos()
}

const handleComplete = async (id) => {
  try {
    await updateTodoStatus(id, 'completed')
    message.success('已标记完成')
    fetchTodos()
  } catch (err) {
    message.error('操作失败')
  }
}

const handleCancel = async (id) => {
  try {
    await updateTodoStatus(id, 'cancelled')
    message.success('已取消')
    fetchTodos()
  } catch (err) {
    message.error('操作失败')
  }
}

const handleReopen = async (id) => {
  try {
    await updateTodoStatus(id, 'pending')
    message.success('已重新打开')
    fetchTodos()
  } catch (err) {
    message.error('操作失败')
  }
}

const handleDelete = async (id) => {
  try {
    await deleteTodo(id)
    message.success('已删除')
    fetchTodos()
  } catch (err) {
    message.error('删除失败')
  }
}

const priorityColor = (p) => ({ high: 'red', medium: 'orange', low: 'blue' }[p] || 'default')
const priorityLabel = (p) => ({ high: '紧急', medium: '一般', low: '可选' }[p] || p)
const statusColor = (s) => ({ pending: 'blue', completed: 'green', cancelled: 'default' }[s] || 'default')
const statusLabel = (s) => ({ pending: '待处理', completed: '已完成', cancelled: '已取消' }[s] || s)

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

onMounted(() => fetchTodos())
</script>

<style scoped>
.todo-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.todo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.todo-header h2 {
  margin: 0;
  font-size: 20px;
}

.todo-stats {
  margin-bottom: 16px;
}

.stat-card {
  border-radius: 8px;
  text-align: center;
  padding: 12px 0;
}

.stat-card.stat-pending { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); color: #fff; }
.stat-card.stat-completed { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: #fff; }
.stat-card.stat-total { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; }

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 4px;
}

.todo-filters {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 6px;
}

.todo-list {
  min-height: 200px;
}

.todo-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 14px 16px;
  transition: box-shadow 0.2s;
}

.todo-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.todo-card.todo-high {
  border-left: 3px solid #ff4d4f;
}

.todo-card.todo-completed {
  opacity: 0.65;
}

.todo-card.todo-cancelled {
  opacity: 0.5;
}

.todo-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.todo-card-meta {
  display: flex;
  gap: 4px;
}

.todo-time {
  color: #999;
  font-size: 13px;
}

.todo-card-body {
  margin-bottom: 8px;
}

.todo-content {
  color: #333;
  line-height: 1.6;
  margin: 0;
}

.todo-content.line-through {
  text-decoration: line-through;
  color: #999;
}

.todo-card-footer {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.todo-pagination {
  margin-top: 20px;
  text-align: center;
}

@media (max-width: 768px) {
  .todo-container {
    padding: 12px;
  }

  .stat-value {
    font-size: 22px;
  }
}
</style>
