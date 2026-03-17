<template>
  <div class="archive-container">
    <div class="archive-header">
      <h2>知识归档</h2>
      <a-button type="primary" @click="refreshList">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <!-- 搜索栏 -->
    <div class="archive-search">
      <a-space :size="12" wrap>
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索归档内容..."
          style="width: 300px"
          allow-clear
          @search="handleSearch"
          @pressEnter="handleSearch"
        />
        <a-select
          v-model:value="filters.contentType"
          placeholder="内容类型"
          allowClear
          style="width: 120px"
          @change="handleFilterChange"
        >
          <a-select-option value="text">文本</a-select-option>
          <a-select-option value="url">链接</a-select-option>
          <a-select-option value="image">图片</a-select-option>
          <a-select-option value="audio">音频</a-select-option>
          <a-select-option value="file">文件</a-select-option>
        </a-select>
        <a-select
          v-model:value="filters.sourceType"
          placeholder="来源"
          allowClear
          style="width: 120px"
          @change="handleFilterChange"
        >
          <a-select-option value="wechat">微信</a-select-option>
          <a-select-option value="wx-official">公众号</a-select-option>
          <a-select-option value="web-manual">手动输入</a-select-option>
          <a-select-option value="feishu">飞书</a-select-option>
        </a-select>
        <a-select
          v-model:value="filters.status"
          placeholder="状态"
          allowClear
          style="width: 120px"
          @change="handleFilterChange"
        >
          <a-select-option value="parsed">已解析</a-select-option>
          <a-select-option value="archived">已归档</a-select-option>
          <a-select-option value="synced">已同步</a-select-option>
        </a-select>
      </a-space>
    </div>

    <!-- 统计栏 -->
    <div class="archive-stats" v-if="total > 0">
      <span class="stats-text">共 {{ total }} 条归档记录</span>
    </div>

    <!-- 内容列表 -->
    <div class="archive-list">
      <a-spin :spinning="loading">
        <a-empty v-if="!loading && messages.length === 0" description="暂无归档内容" />
        <div v-else class="archive-cards">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="archive-card"
          >
            <div class="archive-card-header">
              <div class="archive-meta">
                <a-tag :color="sourceTypeColor(msg.sourceType)" size="small">
                  {{ sourceTypeLabel(msg.sourceType) }}
                </a-tag>
                <a-tag size="small">{{ contentTypeLabel(msg.contentType) }}</a-tag>
                <a-tag :color="statusColor(msg.status)" size="small">
                  {{ statusLabel(msg.status) }}
                </a-tag>
              </div>
              <span class="archive-time">{{ formatTime(msg.receivedAt) }}</span>
            </div>
            <div class="archive-card-body">
              <div
                class="archive-content"
                :class="{ 'content-collapsed': !expandedIds.has(msg.id) }"
              >
                <p v-if="msg.contentType === 'url' && msg.rawContent" class="archive-url">
                  <a :href="msg.rawContent" target="_blank" rel="noopener">{{ msg.rawContent }}</a>
                </p>
                <p class="archive-text">{{ msg.normalizedContent || msg.rawContent || '' }}</p>
              </div>
              <a-button
                v-if="getContentLength(msg) > 200"
                type="link"
                size="small"
                @click="toggleExpand(msg.id)"
              >
                {{ expandedIds.has(msg.id) ? '收起' : '展开全文' }}
              </a-button>
            </div>
            <div class="archive-card-footer" v-if="msg.syncStatus || msg.syncTargets">
              <span class="sync-info" v-if="msg.syncTargets">
                同步至: {{ msg.syncTargets }}
              </span>
              <span class="sync-time" v-if="msg.syncedAt">
                {{ formatTime(msg.syncedAt) }}
              </span>
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <!-- 分页 -->
    <div class="archive-pagination" v-if="total > 0">
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
import { getArchivedMessages } from '@/api/inbox'

const loading = ref(false)
const messages = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const expandedIds = ref(new Set())

const filters = reactive({
  contentType: undefined,
  sourceType: undefined,
  status: undefined,
})

const fetchMessages = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value,
      archived: true, // 只查已归档的
    }
    if (filters.sourceType) params.sourceType = filters.sourceType
    if (filters.status) params.status = filters.status
    if (filters.contentType) params.contentType = filters.contentType
    if (keyword.value.trim()) params.keyword = keyword.value.trim()

    const res = await getArchivedMessages(params)
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      messages.value = data.content || []
      total.value = data.totalElements || 0
    }
  } catch (err) {
    console.error('获取归档列表失败:', err)
    message.error('获取归档列表失败')
  } finally {
    loading.value = false
  }
}

const refreshList = () => {
  currentPage.value = 1
  fetchMessages()
}

const handleSearch = () => {
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
  const colors = { parsed: 'cyan', archived: 'green', synced: 'green' }
  return colors[status] || 'default'
}

const statusLabel = (status) => {
  const labels = { parsed: '已解析', archived: '已归档', synced: '已同步' }
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
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  fetchMessages()
})
</script>

<style scoped>
.archive-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.archive-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.archive-header h2 {
  margin: 0;
  font-size: 20px;
}

.archive-search {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 6px;
}

.archive-stats {
  margin-bottom: 12px;
}

.stats-text {
  color: #999;
  font-size: 13px;
}

.archive-list {
  min-height: 200px;
}

.archive-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.archive-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.archive-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.archive-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.archive-meta {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.archive-time {
  color: #999;
  font-size: 13px;
  white-space: nowrap;
}

.archive-card-body {
  margin-bottom: 4px;
}

.archive-content {
  overflow: hidden;
}

.archive-content.content-collapsed {
  max-height: 120px;
  overflow: hidden;
  position: relative;
}

.archive-content.content-collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  background: linear-gradient(transparent, #fff);
}

.archive-url {
  margin: 0 0 8px 0;
}

.archive-url a {
  color: #1890ff;
  word-break: break-all;
}

.archive-text {
  color: #333;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.archive-card-footer {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f5f5f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sync-info {
  color: #666;
  font-size: 12px;
}

.sync-time {
  color: #999;
  font-size: 12px;
}

.archive-pagination {
  margin-top: 20px;
  text-align: center;
}
</style>
