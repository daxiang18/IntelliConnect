<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Card,
  Tag,
  Button,
  Empty,
  Spin,
  message,
  Alert,
  Form,
  Select,
  Input,
  Popconfirm,
} from 'ant-design-vue'
import { RightOutlined } from '@ant-design/icons-vue'
import { getLlmProviderInformation } from '@/api/llmProviderInformation'
import { getHubLlmModel, postHubLlmModel, deleteHubLlmModel } from '@/api/productLlmModel'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const models = ref([])
const providers = ref([])

const formState = ref({
  providerId: undefined,
  modelName: '',
  toolsId: undefined,
})

const toolOptions = [
  { value: 'classifier', label: '内容分类路由' },
  { value: '5', label: '对话助手' },
  { value: 'memory', label: '记忆提取' },
  { value: 'longMemory', label: '长期记忆' },
  { value: 'knowledgeGraphic', label: '知识图谱' },
]

const toolTypeLabels = {
  classifier: { label: '内容分类路由', color: 'blue' },
  '5': { label: '对话助手', color: 'green' },
  memory: { label: '记忆提取', color: 'purple' },
  longMemory: { label: '长期记忆', color: 'cyan' },
  knowledgeGraphic: { label: '知识图谱', color: 'orange' },
}

const providerNameMap = computed(() => {
  const map = {}
  for (const item of providers.value) {
    map[item.value] = item.label
  }
  return map
})

const groupedModels = computed(() => {
  const groups = {}
  for (const model of models.value) {
    const type = model.toolsId || 'other'
    if (!groups[type]) {
      groups[type] = []
    }
    groups[type].push({
      ...model,
      providerName: providerNameMap.value[model.providerId] || `服务商 #${model.providerId}`,
    })
  }
  return groups
})

function getToolTypeInfo(type) {
  return toolTypeLabels[type] || { label: type, color: 'default' }
}

function resetForm() {
  formState.value = {
    providerId: undefined,
    modelName: '',
    toolsId: undefined,
  }
}

function fetchProviders() {
  return getLlmProviderInformation()
    .then((res) => {
      const { data, errorCode } = res.data
      if (errorCode === 2001) {
        router.push('/login')
        return
      }
      if (errorCode === 200 && Array.isArray(data)) {
        providers.value = data.map((item) => ({
          value: item.id,
          label: item.userName ? `${item.providerName} (${item.userName})` : item.providerName,
        }))
      } else {
        providers.value = []
      }
    })
    .catch(() => {
      message.error('获取模型服务商失败')
    })
}

function fetchModels() {
  loading.value = true
  getHubLlmModel()
    .then((res) => {
      const { data, errorCode } = res.data
      if (errorCode === 2001) {
        router.push('/login')
        return
      }
      if (errorCode === 200 && Array.isArray(data)) {
        models.value = data.filter((item) => toolOptions.some((tool) => tool.value === item.toolsId))
      } else {
        models.value = []
      }
    })
    .catch(() => {
      message.error('获取个人中枢模型配置失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSave() {
  if (!formState.value.providerId || !formState.value.modelName || !formState.value.toolsId) {
    message.warning('请填写完整模型配置')
    return
  }
  saving.value = true
  postHubLlmModel({ ...formState.value })
    .then((res) => {
      const { errorCode } = res.data
      if (errorCode === 200) {
        message.success('保存成功')
        resetForm()
        fetchModels()
      } else if (errorCode === 2001) {
        router.push('/login')
      } else {
        message.error('保存失败')
      }
    })
    .catch(() => {
      message.error('保存失败')
    })
    .finally(() => {
      saving.value = false
    })
}

function handleDelete(id) {
  deleteHubLlmModel({ id })
    .then((res) => {
      const { errorCode } = res.data
      if (errorCode === 200) {
        message.success('删除成功')
        fetchModels()
      } else if (errorCode === 2001) {
        router.push('/login')
      } else {
        message.error('删除失败')
      }
    })
    .catch(() => {
      message.error('删除失败')
    })
}

function goToModelConfig() {
  router.push('/productLlmModel')
}

onMounted(() => {
  fetchProviders()
  fetchModels()
})
</script>

<template>
  <div class="tab-models">
    <Alert
      type="info"
      show-icon
      class="section-tip"
      message="这里统一配置个人中枢入口使用的智能模型。"
      description="保存后会覆盖对应使用场景的当前模型，下面同时展示当前生效配置。"
    />

    <Card title="新增/更新模型配置" size="small" class="config-card">
      <Form layout="vertical">
        <div class="form-grid">
          <Form.Item label="使用场景">
            <Select
              v-model:value="formState.toolsId"
              :options="toolOptions"
              placeholder="请选择使用场景"
            />
          </Form.Item>
          <Form.Item label="模型服务商">
            <Select
              v-model:value="formState.providerId"
              :options="providers"
              placeholder="请选择模型服务商"
            />
          </Form.Item>
        </div>
        <Form.Item label="模型名称">
          <Input v-model:value="formState.modelName" placeholder="请输入模型名称，例如 gpt-4o-mini / claude-sonnet-4-6" />
        </Form.Item>
        <div class="save-bar">
          <Button type="primary" :loading="saving" @click="handleSave">保存当前配置</Button>
        </div>
      </Form>
    </Card>

    <Spin :spinning="loading">
      <div v-if="models.length > 0" class="models-grid">
        <Card v-for="(modelList, toolType) in groupedModels" :key="toolType" size="small" class="model-card">
          <template #title>
            <Tag :color="getToolTypeInfo(toolType).color">
              {{ getToolTypeInfo(toolType).label }}
            </Tag>
          </template>
          <div v-for="model in modelList" :key="model.id" class="model-item">
            <div class="model-main">
              <div>
                <div class="model-name">{{ model.modelName || '未命名模型' }}</div>
                <div class="model-info">
                  <span class="provider">{{ model.providerName }}</span>
                  <Tag color="cyan" size="small">个人中枢</Tag>
                </div>
              </div>
              <Popconfirm title="确认删除这条模型配置吗？" @confirm="handleDelete(model.id)">
                <Button danger type="link">删除</Button>
              </Popconfirm>
            </div>
          </div>
        </Card>
      </div>
      <Empty v-else-if="!loading" description="个人中枢暂无模型配置" />
    </Spin>

    <div class="action-bar">
      <Button @click="goToModelConfig">前往通用模型配置页 <RightOutlined /></Button>
    </div>
  </div>
</template>

<style scoped>
.tab-models {
  padding-top: 8px;
}

.section-tip {
  margin-bottom: 16px;
}

.config-card {
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.save-bar {
  display: flex;
  justify-content: flex-end;
}

.models-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.model-card {
  border-radius: 8px;
}

.model-item {
  padding: 8px 0;
}

.model-item + .model-item {
  border-top: 1px solid #f5f5f5;
}

.model-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.model-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.model-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #999;
}

.provider {
  color: #666;
}

.action-bar {
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}
</style>
