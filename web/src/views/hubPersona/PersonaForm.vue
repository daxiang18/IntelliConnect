<script setup>
import { reactive, watch, defineProps, defineEmits } from 'vue'
import { Modal, Form, Input, Select, InputNumber, Switch, message } from 'ant-design-vue'
import { createHubPersona, updateHubPersona } from '@/api/hubPersona'

const props = defineProps({
  visible: { type: Boolean, default: false },
  productId: { type: Number, default: null },
  editData: { type: Object, default: null },
})
const emit = defineEmits(['update:visible', 'refresh'])

const defaultSystemPrompt = `你是一个个人知识管理助手。请分析以下用户输入内容，返回严格 JSON 格式（不要输出 markdown 代码块）：

{
  "category": "分类",
  "tags": ["标签1", "标签2"],
  "summary": "一句话摘要",
  "todos": [
    {"content": "待办内容", "priority": "high/medium/low"}
  ],
  "entities": ["实体1", "实体2"]
}

分类规则（category 必须是以下之一）：
- task: 包含明确的任务、待办事项、TODO
- meeting: 会议记录、会议安排
- question: 提问、疑问
- idea: 创意、灵感、想法
- knowledge: 知识点、学习笔记、技术文档
- reference: 参考资料、链接收藏
- note: 日常记录、随手笔记
- schedule: 日程、时间安排

标签规则（tags）：
- 提取 3-5 个关键主题标签
- 使用中文标签
- 如果内容涉及技术，标注技术名称

摘要规则（summary）：
- 用一句话概括内容核心
- 不超过 50 字

待办提取规则（todos）：
- 只提取明确的行动项
- 如果没有待办，返回空数组
- priority: high=紧急/重要, medium=一般, low=可选

实体提取规则（entities）：
- 提取人名、组织、技术名词、项目名等关键实体
- 最多 5 个
- 如果没有明确实体，返回空数组`

const formState = reactive({
  id: null,
  productId: null,
  personaName: '',
  systemPrompt: defaultSystemPrompt,
  summaryStyle: 'concise',
  language: 'zh',
  maxTags: 5,
  maxEntities: 5,
  enabled: true,
})

const submitting = reactive({ value: false })

watch(
  () => props.editData,
  (val) => {
    if (val) {
      formState.id = val.id
      formState.productId = val.productId
      formState.personaName = val.personaName
      formState.systemPrompt = val.systemPrompt || defaultSystemPrompt
      formState.summaryStyle = val.summaryStyle || 'concise'
      formState.language = val.language || 'zh'
      formState.maxTags = val.maxTags ?? 5
      formState.maxEntities = val.maxEntities ?? 5
      formState.enabled = val.enabled ?? true
    } else {
      formState.id = null
      formState.productId = props.productId
      formState.personaName = ''
      formState.systemPrompt = defaultSystemPrompt
      formState.summaryStyle = 'concise'
      formState.language = 'zh'
      formState.maxTags = 5
      formState.maxEntities = 5
      formState.enabled = true
    }
  },
  { immediate: true }
)

watch(
  () => props.productId,
  (val) => {
    if (!formState.id) {
      formState.productId = val
    }
  }
)

function handleOk() {
  if (!formState.personaName || !formState.systemPrompt) {
    message.warning('请填写必填字段')
    return
  }
  submitting.value = true
  const payload = {
    ...formState,
    productId: formState.productId || props.productId,
  }
  const api = formState.id ? updateHubPersona : createHubPersona
  api(payload)
    .then((res) => {
      const { errorCode } = res.data
      if (errorCode === 200) {
        message.success(formState.id ? '更新成功' : '创建成功')
        emit('update:visible', false)
        emit('refresh')
      } else {
        message.error('操作失败')
      }
    })
    .catch(() => {
      message.error('操作失败')
    })
    .finally(() => {
      submitting.value = false
    })
}

function handleCancel() {
  emit('update:visible', false)
}
</script>

<template>
  <Modal
    :open="visible"
    :title="formState.id ? '编辑人设' : '新建人设'"
    :confirm-loading="submitting.value"
    width="700px"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <Form :model="formState" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }" style="margin-top: 16px">
      <Form.Item label="人设名称" required>
        <Input v-model:value="formState.personaName" placeholder="如：技术笔记助手" :maxlength="100" />
      </Form.Item>
      <Form.Item label="系统提示词" required>
        <Input.TextArea
          v-model:value="formState.systemPrompt"
          :rows="10"
          placeholder="自定义 AI 分析系统提示词"
          :maxlength="10000"
          show-count
        />
      </Form.Item>
      <Form.Item label="摘要风格">
        <Select v-model:value="formState.summaryStyle">
          <Select.Option value="concise">简洁</Select.Option>
          <Select.Option value="detailed">详细</Select.Option>
          <Select.Option value="bullet">要点</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="输出语言">
        <Select v-model:value="formState.language">
          <Select.Option value="zh">中文</Select.Option>
          <Select.Option value="en">英文</Select.Option>
          <Select.Option value="auto">自动</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item label="最大标签数">
        <InputNumber v-model:value="formState.maxTags" :min="1" :max="20" />
      </Form.Item>
      <Form.Item label="最大实体数">
        <InputNumber v-model:value="formState.maxEntities" :min="1" :max="20" />
      </Form.Item>
      <Form.Item label="启用">
        <Switch v-model:checked="formState.enabled" />
      </Form.Item>
    </Form>
  </Modal>
</template>
