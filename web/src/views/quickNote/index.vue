<template>
  <div class="quicknote-container">
    <div class="quicknote-header">
      <h2>快速速记</h2>
      <span class="quicknote-tip">Ctrl + Enter 快速保存</span>
    </div>
    <div class="quicknote-input">
      <a-textarea
        ref="textareaRef"
        v-model:value="noteContent"
        placeholder="记录想法、笔记、链接..."
        :auto-size="{ minRows: 4, maxRows: 12 }"
        show-count
        :maxlength="5000"
        @keydown="handleKeydown"
      />
      <div class="quicknote-toolbar">
        <div class="toolbar-left">
          <a-select v-model:value="contentType" style="width: 100px" size="small">
            <a-select-option value="text">文本</a-select-option>
            <a-select-option value="url">链接</a-select-option>
          </a-select>
        </div>
        <div class="toolbar-right">
          <span class="save-hint" v-if="noteContent.trim()">Ctrl+Enter</span>
          <a-button
            type="primary"
            :loading="submitting"
            :disabled="!noteContent.trim()"
            @click="handleSubmit"
          >
            保存
          </a-button>
        </div>
      </div>
    </div>

    <!-- 最近记录 -->
    <div class="recent-notes">
      <div class="recent-header">
        <h3>最近记录</h3>
        <a-button type="link" size="small" @click="$router.push('/inbox')" v-if="recentNotes.length > 0">
          查看全部
        </a-button>
      </div>

      <a-empty v-if="recentNotes.length === 0 && !loadingRecent" description="还没有记录，开始写点什么吧" />

      <a-spin :spinning="loadingRecent">
        <div v-for="note in recentNotes" :key="note.id" class="recent-note-item">
          <div class="note-main">
            <div class="note-meta">
              <a-tag :color="statusColor(note.status)" size="small">
                {{ statusLabel(note.status) }}
              </a-tag>
              <a-tag v-if="note.contentType === 'url'" size="small" color="blue">链接</a-tag>
              <span class="note-time">{{ formatTime(note.receivedAt) }}</span>
            </div>
            <p class="note-content">{{ truncate(note.normalizedContent || note.rawContent || '', 150) }}</p>
          </div>
          <div class="note-actions">
            <a-popconfirm
              v-if="note.status === 'received'"
              title="确定要处理这条记录吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleProcess(note.id)"
            >
              <a-button type="link" size="small">
                <template #icon><ThunderboltOutlined /></template>
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'
import { createMessage, getInboxMessages, processMessage } from '@/api/inbox'

const textareaRef = ref(null)
const noteContent = ref('')
const contentType = ref('text')
const submitting = ref(false)
const recentNotes = ref([])
const loadingRecent = ref(false)

const handleKeydown = (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    handleSubmit()
  }
}

const handleSubmit = async () => {
  if (!noteContent.value.trim() || submitting.value) return
  submitting.value = true
  try {
    // 自动检测 URL
    const content = noteContent.value.trim()
    let type = contentType.value
    if (type === 'text' && /^https?:\/\/\S+$/i.test(content)) {
      type = 'url'
    }

    const res = await createMessage({
      sourceType: 'web-manual',
      contentType: type,
      rawContent: content,
      sessionId: 'quicknote',
    })
    const { errorCode } = res.data
    if (errorCode === 200) {
      message.success('保存成功')
      noteContent.value = ''
      contentType.value = 'text'
      fetchRecent()
    } else {
      message.error('保存失败')
    }
  } catch (err) {
    console.error('保存失败:', err)
    message.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const handleProcess = async (id) => {
  try {
    await processMessage(id)
    message.success('已提交处理')
    fetchRecent()
  } catch (err) {
    message.error('处理失败')
  }
}

const fetchRecent = async () => {
  loadingRecent.value = true
  try {
    const res = await getInboxMessages({ sourceType: 'web-manual', page: 0, size: 8 })
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      recentNotes.value = data.content || []
    }
  } catch (err) {
    console.error('获取最近记录失败:', err)
  } finally {
    loadingRecent.value = false
  }
}

const truncate = (text, len) => {
  return text.length > len ? text.substring(0, len) + '...' : text
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
  fetchRecent()
})
</script>

<style scoped>
.quicknote-container {
  padding: 20px;
  max-width: 700px;
  margin: 0 auto;
}

.quicknote-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16px;
}

.quicknote-header h2 {
  margin: 0;
  font-size: 20px;
}

.quicknote-tip {
  color: #bbb;
  font-size: 12px;
}

.quicknote-input {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
}

.quicknote-toolbar {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.save-hint {
  color: #bbb;
  font-size: 12px;
}

/* 最近记录 */
.recent-notes {
  margin-top: 24px;
}

.recent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.recent-header h3 {
  font-size: 16px;
  margin: 0;
  color: #666;
}

.recent-note-item {
  background: #fafafa;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  transition: background 0.2s;
}

.recent-note-item:hover {
  background: #f0f0f0;
}

.note-main {
  flex: 1;
  min-width: 0;
}

.note-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.note-time {
  color: #999;
  font-size: 12px;
}

.note-content {
  color: #333;
  margin: 0;
  line-height: 1.5;
  word-break: break-all;
}

.note-actions {
  flex-shrink: 0;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .quicknote-container {
    padding: 12px;
  }

  .quicknote-header h2 {
    font-size: 17px;
  }

  .quicknote-input {
    padding: 12px;
  }

  .quicknote-toolbar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .recent-note-item {
    flex-direction: column;
    gap: 6px;
  }

  .note-actions {
    align-self: flex-end;
  }
}

@media (max-width: 480px) {
  .quicknote-container {
    padding: 8px;
  }

  .quicknote-tip {
    display: none;
  }

  .save-hint {
    display: none;
  }
}
</style>
