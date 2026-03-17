<template>
  <div class="quicknote-container">
    <div class="quicknote-header">
      <h2>快速速记</h2>
    </div>
    <div class="quicknote-input">
      <a-textarea
        v-model:value="noteContent"
        placeholder="记录想法、笔记、链接..."
        :auto-size="{ minRows: 4, maxRows: 12 }"
        show-count
        :maxlength="5000"
      />
      <div class="quicknote-actions">
        <a-space>
          <a-select v-model:value="contentType" style="width: 100px">
            <a-select-option value="text">文本</a-select-option>
            <a-select-option value="url">链接</a-select-option>
          </a-select>
          <a-button
            type="primary"
            :loading="submitting"
            :disabled="!noteContent.trim()"
            @click="handleSubmit"
          >
            保存
          </a-button>
        </a-space>
      </div>
    </div>

    <!-- 最近记录 -->
    <div class="recent-notes" v-if="recentNotes.length > 0">
      <h3>最近记录</h3>
      <div v-for="note in recentNotes" :key="note.id" class="recent-note-item">
        <div class="note-meta">
          <a-tag :color="note.status === 'received' ? 'blue' : 'green'">
            {{ note.status === 'received' ? '待处理' : '已归档' }}
          </a-tag>
          <span class="note-time">{{ formatTime(note.receivedAt) }}</span>
        </div>
        <p class="note-content">{{ (note.normalizedContent || note.rawContent || '').substring(0, 100) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { createMessage, getInboxMessages } from '@/api/inbox'

const noteContent = ref('')
const contentType = ref('text')
const submitting = ref(false)
const recentNotes = ref([])

const handleSubmit = async () => {
  if (!noteContent.value.trim()) return
  submitting.value = true
  try {
    const res = await createMessage({
      sourceType: 'web-manual',
      contentType: contentType.value,
      rawContent: noteContent.value.trim(),
      sessionId: 'quicknote',
    })
    const { errorCode } = res.data
    if (errorCode === 200) {
      message.success('保存成功')
      noteContent.value = ''
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

const fetchRecent = async () => {
  try {
    const res = await getInboxMessages({ sourceType: 'web-manual', page: 0, size: 5 })
    const { data, errorCode } = res.data
    if (errorCode === 200 && data) {
      recentNotes.value = data.content || []
    }
  } catch (err) {
    console.error('获取最近记录失败:', err)
  }
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

.quicknote-header h2 {
  margin: 0 0 16px 0;
  font-size: 20px;
}

.quicknote-input {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
}

.quicknote-actions {
  margin-top: 12px;
  text-align: right;
}

.recent-notes {
  margin-top: 24px;
}

.recent-notes h3 {
  font-size: 16px;
  margin-bottom: 12px;
  color: #666;
}

.recent-note-item {
  background: #fafafa;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
}

.note-meta {
  display: flex;
  align-items: center;
  gap: 8px;
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
}
</style>
