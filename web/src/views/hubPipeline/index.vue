<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Select, Switch, Button, Card, Input, Divider, message } from 'ant-design-vue'
import { SaveOutlined } from '@ant-design/icons-vue'
import { getHubPipeline, createHubPipeline, updateHubPipeline } from '@/api/hubPipeline'
import { getProduct } from '@/api/product'

const router = useRouter()

const products = ref([])
const productLoading = ref(true)
const currentProductId = ref(null)
const loading = ref(false)
const saving = ref(false)
const hasExistingConfig = ref(false)

const config = reactive({
  id: 0,
  productId: null,
  autoProcess: true,
  urlNormalize: true,
  imageVision: true,
  voiceAsr: true,
  aiAnalysis: true,
  todoExtraction: true,
  entityExtraction: true,
  vectorIngest: true,
  knowledgeGraphLink: false,
  feishuSync: false,
  githubSync: false,
  routingPrompt: '',
})

function resetConfig() {
  config.id = 0
  config.autoProcess = true
  config.urlNormalize = true
  config.imageVision = true
  config.voiceAsr = true
  config.aiAnalysis = true
  config.todoExtraction = true
  config.entityExtraction = true
  config.vectorIngest = true
  config.knowledgeGraphLink = false
  config.feishuSync = false
  config.githubSync = false
  config.routingPrompt = ''
  hasExistingConfig.value = false
}

function fetchProducts() {
  getProduct()
    .then((res) => {
      const { data, errorCode } = res.data
      if (errorCode === 2001) {
        router.push('/login')
        return
      }
      if (errorCode === 200 && data && Array.isArray(data)) {
        products.value = data.map((item) => ({
          key: item.id,
          value: item.id,
          label: item.productName,
        }))
        productLoading.value = false
        currentProductId.value = data.length > 0 ? data[0].id : null
        if (currentProductId.value !== null) {
          fetchConfig()
        }
      } else {
        products.value = []
        productLoading.value = false
      }
    })
    .catch(() => {
      productLoading.value = false
    })
}

function fetchConfig() {
  if (!currentProductId.value) return
  loading.value = true
  getHubPipeline({ productId: currentProductId.value })
    .then((res) => {
      const { data, errorCode } = res.data
      if (errorCode === 2001) {
        router.push('/login')
        return
      }
      if (errorCode === 200 && data) {
        Object.assign(config, data)
        config.productId = currentProductId.value
        hasExistingConfig.value = data.id > 0
      }
    })
    .catch(() => {
      message.error('获取配置失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function handleProductChange(value) {
  currentProductId.value = value
  resetConfig()
  fetchConfig()
}

function handleSave() {
  saving.value = true
  const payload = {
    ...config,
    productId: currentProductId.value,
  }
  const api = hasExistingConfig.value ? updateHubPipeline : createHubPipeline
  api(payload)
    .then((res) => {
      const { errorCode } = res.data
      if (errorCode === 200) {
        message.success('保存成功')
        fetchConfig()
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

onMounted(() => {
  fetchProducts()
})
</script>

<template>
  <div class="pipeline-wrapper">
    <div class="pipeline-main">
      <div class="pipeline-header">
        <div class="option-item">
          <label class="option-label">产品</label>
          <Select
            class="product-select"
            :value="currentProductId"
            :options="products"
            :loading="productLoading"
            style="min-width: 180px"
            @change="handleProductChange"
          />
        </div>
      </div>

      <div v-if="currentProductId" class="pipeline-body">
        <Card title="&#x1F4E5; 输入处理" size="small" class="config-card">
          <div class="switch-row">
            <span class="switch-label">消息创建后自动处理</span>
            <Switch v-model:checked="config.autoProcess" />
          </div>
        </Card>

        <Card title="&#x1F504; 内容预处理" size="small" class="config-card">
          <div class="switch-row">
            <span class="switch-label">URL 内容归一化</span>
            <Switch v-model:checked="config.urlNormalize" />
          </div>
          <div class="switch-row">
            <span class="switch-label">图片视觉理解</span>
            <Switch v-model:checked="config.imageVision" />
          </div>
          <div class="switch-row">
            <span class="switch-label">语音 ASR 转写</span>
            <Switch v-model:checked="config.voiceAsr" />
          </div>
        </Card>

        <Card title="&#x1F916; AI 分析" size="small" class="config-card">
          <div class="switch-row">
            <span class="switch-label">AI 内容分析</span>
            <Switch v-model:checked="config.aiAnalysis" />
          </div>
          <div class="switch-row">
            <span class="switch-label">待办自动提取</span>
            <Switch v-model:checked="config.todoExtraction" />
          </div>
          <div class="switch-row">
            <span class="switch-label">实体自动提取</span>
            <Switch v-model:checked="config.entityExtraction" />
          </div>
        </Card>

        <Card title="&#x1F4BE; 数据存储" size="small" class="config-card">
          <div class="switch-row">
            <span class="switch-label">向量数据库入库</span>
            <Switch v-model:checked="config.vectorIngest" />
          </div>
          <div class="switch-row">
            <span class="switch-label">知识图谱自动关联</span>
            <Switch v-model:checked="config.knowledgeGraphLink" />
          </div>
        </Card>

        <Card title="&#x1F517; 同步设置" size="small" class="config-card">
          <div class="switch-row">
            <span class="switch-label">飞书自动同步</span>
            <Switch v-model:checked="config.feishuSync" />
          </div>
          <div class="switch-row">
            <span class="switch-label">GitHub 自动同步</span>
            <Switch v-model:checked="config.githubSync" />
          </div>
        </Card>

        <Card title="&#x1F4DD; 路由策略" size="small" class="config-card">
          <Input.TextArea
            v-model:value="config.routingPrompt"
            :rows="4"
            placeholder="自定义语义路由/分类策略提示词（可选）"
            :maxlength="5000"
            show-count
          />
        </Card>

        <div class="save-bar">
          <Button type="primary" size="large" :loading="saving" @click="handleSave">
            <SaveOutlined /> 保存配置
          </Button>
        </div>
      </div>

      <div v-else class="empty-state">
        <p>请先选择一个产品</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pipeline-wrapper {
  height: 100%;
  padding: 24px;
  background-color: #f0f2f5;
}

.pipeline-main {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.pipeline-header {
  display: flex;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-label {
  font-weight: 500;
  white-space: nowrap;
}

.pipeline-body {
  max-width: 700px;
}

.config-card {
  margin-bottom: 16px;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.switch-row + .switch-row {
  border-top: 1px solid #f5f5f5;
}

.switch-label {
  font-size: 14px;
  color: #333;
}

.save-bar {
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #999;
  font-size: 16px;
}
</style>
