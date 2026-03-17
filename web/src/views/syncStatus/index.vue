<template>
  <div class="sync-container">
    <div class="sync-header">
      <h2>同步状态面板</h2>
      <a-button type="primary" @click="refreshAll">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <!-- 统计卡片 -->
    <a-row :gutter="[16, 16]" class="sync-stats">
      <a-col :xs="12" :sm="6">
        <a-card class="stat-card stat-total" :bordered="false" :loading="statsLoading">
          <div class="stat-value">{{ stats.totalSyncable || 0 }}</div>
          <div class="stat-label">同步消息总数</div>
        </a-card>
      </a-col>
      <a-col :xs="12" :sm="6">
        <a-card class="stat-card stat-synced" :bordered="false" :loading="statsLoading">
          <div class="stat-value">{{ stats.bySyncStatus?.synced || 0 }}</div>
          <div class="stat-label">已同步</div>
        </a-card>
      </a-col>
      <a-col :xs="12" :sm="6">
        <a-card class="stat-card stat-pending" :bordered="false" :loading="statsLoading">
          <div class="stat-value">{{ stats.bySyncStatus?.pending || 0 }}</div>
          <div class="stat-label">待同步</div>
        </a-card>
      </a-col>
      <a-col :xs="12" :sm="6">
        <a-card class="stat-card stat-failed" :bordered="false" :loading="statsLoading">
          <div class="stat-value">{{ stats.bySyncStatus?.failed || 0 }}</div>
          <div class="stat-label">同步失败</div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 同步成功率进度条 -->
    <a-card class="sync-progress-card" :bordered="false" v-if="stats.totalSyncable > 0">
      <div class="progress-header">
        <span class="progress-title">同步完成率</span>
        <span class="progress-percent">{{ syncPercent }}%</span>
      </div>
      <a-progress
        :percent="syncPercent"
        :stroke-color="syncPercent >= 80 ? '#52c41a' : syncPercent >= 50 ? '#faad14' : '#ff4d4f'"
        :show-info="false"
      />
      <div class="progress-detail">
        <span>已同步 {{ stats.bySyncStatus?.synced || 0 }} / 共 {{ stats.totalSyncable }} 条</span>
      </div>
    </a-card>

    <!-- 筛选栏 -->
    <div class="sync-filters">
      <a-space :size="12" wrap>
        <a-select
          v-model:value="filterStatus"
          placeholder="同步状态"
          allowClear
          style="width: 140px"
          @change="handleFilterChange"
        >
          <a-select-option value="synced">已同步</a-select-option>
          <a-select-option value="pending">待同步</a-select-option>
          <a-select-option value="failed">同步失败</a-select-option>
          <a-select-option value="not_requested">未请求</a-select-option>
        </a-select>
      </a-space>
    </div>

    <!-- 同步消息列表 -->
    <div class="sync-list">
      <a-spin :spinning="listLoading">
        <a-empty v-if="!listLoading && messages.length === 0" description="暂无同步消息" />
        <div v-else class="sync-cards">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="sync-card"
            :class="{ 'sync-failed': msg.syncStatus === 'failed' }"
          >
            <div class="sync-card-header">
              <div class="sync-card-meta">
                <a-tag :color="syncStatusColor(msg.syncStatus)" size="small">
                  {{ syncStatusLabel(msg.syncStatus) }}
                </a-tag>
                <a-tag size="small">{{ contentTypeLabel(msg.contentType) }}</a-tag>
                <a-tag :color="sourceTypeColor(msg.sourceType)" size="small">
                  {{ sourceTypeLabel(msg.sourceType) }}
                </a-tag>
              </div>
              <div class="sync-card-actions">
                <span class="sync-time">{{ formatTime(msg.syncedAt || msg.receivedAt) }}</span>
                <a-button type="link" size="small" class="detail-link" @click="$router.push(`/messageDetail/${msg.id}`)">
                  详情
                </a-button>
              </div>
            </div>

            <div class="sync-card-body">
              <p class="sync-content">{{ truncateContent(msg.normalizedContent || msg.rawContent || '', 150) }}</p>
            </div>

            <!-- 同步目标详情 -->
            <div class="sync-card-targets" v-if="msg.syncTargets">
              <div class="targets-header">同步目标</div>
              <div class="targets-list">
                <div v-for="target in parseSyncTargets(msg.syncTargets)" :key="target" class="target-item">
                  <a-tag :color="getTargetColor(target)">{{ getTargetLabel(target) }}</a-tag>
                  <template v-if="getTargetRef(msg, target)">
                    <a-tag v-if="getTargetRef(msg, target).status === 'synced'" color="green" size="small">
                      <CheckCircleOutlined /> 已同步
                    </a-tag>
                    <a-tag v-else-if="getTargetRef(msg, target).status === 'failed'" color="red" size="small">
                      <CloseCircleOutlined /> 失败
                    </a-tag>
                    <a-tag v-else color="blue" size="small">
                      <SyncOutlined :spin="true" /> 等待中
                    </a-tag>
                    <!-- 外部链接 -->
                    <a
                      v-if="getTargetRef(msg, target).documentUrl"
                      :href="getTargetRef(msg, target).documentUrl"
                      target="_blank"
                      rel="noopener"
                      class="ext-link"
                      @click.stop
                    >
                      查看文档
                    </a>
                    <a
                      v-else-if="getTargetRef(msg, target).wikiUrl"
                      :href="getTargetRef(msg, target).wikiUrl"
                      target="_blank"
                      rel="noopener"
                      class="ext-link"
                      @click.stop
                    >
                      查看知识库
                    </a>
                    <!-- 错误信息 -->
                    <span v-if="getTargetRef(msg, target).errorMessage" class="error-msg">
                      {{ getTargetRef(msg, target).errorMessage }}
                    </span>
                  </template>
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="sync-card-footer" v-if="msg.syncStatus === 'failed'">
              <a-popconfirm
                title="重新处理将重新触发同步，确定吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleRetry(msg.id)"
              >
                <a-button size="small" type="link" danger>
                  <template #icon><RedoOutlined /></template>
                  重新同步
                </a-button>
              </a-popconfirm>
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <!-- 分页 -->
    <div class="sync-pagination" v-if="total > 0">
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
  RedoOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons-vue'
import { retryMessage } from '@/api/inbox'
import request from '@/utils/request'

// API
const getSyncStats = () => request({ url: '/api/v2/input/messages/sync-stats', method: 'get' })
const getSyncMessages = (params) => request({ url: '/api/v2/input/messages/sync-list', method: 'get', params })

const statsLoading = ref(false)
const listLoading = ref(false)
const stats = ref({})
const messages = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterStatus = ref(undefined)

const syncPercent = computed(() => {
  const t = stats.value.totalSyncable || 0
  const s = stats.value.bySyncStatus?.synced || 0
  if (t === 0) return 0
  return Math.round((s / t) * 100)
})

const fetchStats = async () => {
  statsLoading.value = true
  try {
    const res = await getSyncStats()
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      stats.value = data
    }
  } catch (err) {
    console.error('获取同步统计失败:', err)
  } finally {
    statsLoading.value = false
  }
}

const fetchMessages = async () => {
  listLoading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value,
    }
    if (filterStatus.value) params.syncStatus = filterStatus.value

    const res = await getSyncMessages(params)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      messages.value = data.content || []
      total.value = data.totalElements || 0
    }
  } catch (err) {
    console.error('获取同步列表失败:', err)
    message.error('获取同步列表失败')
  } finally {
    listLoading.value = false
  }
}

const refreshAll = () => {
  currentPage.value = 1
  fetchStats()
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

const handleRetry = async (id) => {
  try {
    await retryMessage(id)
    message.success('已提交重新同步')
    refreshAll()
  } catch (err) {
    message.error('重试失败')
  }
}

const parseSyncTargets = (targets) => {
  if (!targets) return []
  return targets.split(',').map((t) => t.trim()).filter(Boolean)
}

const getTargetRef = (msg, target) => {
  if (!msg.externalReferencesJson) return null
  try {
    const refs = typeof msg.externalReferencesJson === 'string'
      ? JSON.parse(msg.externalReferencesJson)
      : msg.externalReferencesJson
    return refs[target] || null
  } catch {
    return null
  }
}

const getTargetColor = (target) => {
  const colors = { feishu: 'purple', github: 'geekblue', notion: 'orange' }
  return colors[target] || 'default'
}

const getTargetLabel = (target) => {
  const labels = { feishu: '飞书', github: 'GitHub', notion: 'Notion' }
  return labels[target] || target
}

const truncateContent = (text, maxLen) => {
  if (!text) return ''
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}

const syncStatusColor = (status) => {
  const colors = { synced: 'green', pending: 'blue', failed: 'red', not_requested: 'default' }
  return colors[status] || 'default'
}

const syncStatusLabel = (status) => {
  const labels = { synced: '已同步', pending: '待同步', failed: '同步失败', not_requested: '未请求' }
  return labels[status] || status || '未知'
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

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const d = new Date(timestamp)
  const now = new Date()
  const diffMs = now - d
  if (diffMs < 60000) return '刚刚'
  if (diffMs < 3600000) return Math.floor(diffMs / 60000) + '分钟前'
  if (diffMs < 86400000) return Math.floor(diffMs / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  fetchStats()
  fetchMessages()
})
</script>

<style scoped>
.sync-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.sync-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.sync-header h2 {
  margin: 0;
  font-size: 20px;
}

.sync-stats {
  margin-bottom: 16px;
}

.stat-card {
  border-radius: 8px;
  text-align: center;
  padding: 12px 0;
}

.stat-card.stat-total { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; }
.stat-card.stat-synced { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: #fff; }
.stat-card.stat-pending { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); color: #fff; }
.stat-card.stat-failed { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: #fff; }

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

.sync-progress-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.progress-title {
  font-weight: 600;
  color: #333;
}

.progress-percent {
  font-size: 18px;
  font-weight: 700;
  color: #1890ff;
}

.progress-detail {
  margin-top: 6px;
  color: #999;
  font-size: 13px;
}

.sync-filters {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 6px;
}

.sync-list {
  min-height: 200px;
}

.sync-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sync-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.sync-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.sync-card.sync-failed {
  border-left: 3px solid #ff4d4f;
}

.sync-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.sync-card-meta {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.sync-card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sync-time {
  color: #999;
  font-size: 13px;
  white-space: nowrap;
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

.sync-card-body {
  margin-bottom: 8px;
}

.sync-content {
  color: #555;
  line-height: 1.6;
  margin: 0;
  word-break: break-all;
}

.sync-card-targets {
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: 6px;
  margin-bottom: 4px;
}

.targets-header {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.targets-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.target-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ext-link {
  font-size: 12px;
  color: #1890ff;
}

.error-msg {
  font-size: 12px;
  color: #ff4d4f;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sync-card-footer {
  margin-top: 8px;
  text-align: right;
}

.sync-pagination {
  margin-top: 20px;
  text-align: center;
}

@media (max-width: 768px) {
  .sync-container {
    padding: 12px;
  }

  .sync-header h2 {
    font-size: 17px;
  }

  .stat-value {
    font-size: 22px;
  }

  .stat-label {
    font-size: 12px;
  }

  .sync-card {
    padding: 12px;
  }

  .sync-card-header {
    flex-wrap: wrap;
    gap: 6px;
  }

  .sync-card-meta :deep(.ant-tag) {
    font-size: 11px;
    padding: 0 4px;
    margin-inline-end: 2px;
  }

  .target-item {
    gap: 4px;
  }

  .error-msg {
    max-width: 200px;
  }

  .sync-pagination :deep(.ant-pagination-options) {
    display: none;
  }
}

@media (max-width: 480px) {
  .sync-container {
    padding: 8px;
  }

  .sync-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .stat-value {
    font-size: 18px;
  }

  .error-msg {
    max-width: 150px;
    font-size: 11px;
  }
}
</style>
