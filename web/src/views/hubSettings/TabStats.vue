<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Card, Spin, Empty, Tag, message } from 'ant-design-vue'
import * as echarts from 'echarts'
import { getMessageStats, getCategoryStats } from '@/api/inbox'

const router = useRouter()

const loading = ref(false)
const stats = ref(null)
const categoryData = ref(null)

const sourceChartRef = ref(null)
const purposeChartRef = ref(null)
const categoryChartRef = ref(null)

let sourceChart = null
let purposeChart = null
let categoryChart = null

const statusLabels = {
  received: '待处理',
  processing: '处理中',
  parsed: '已分析',
  archived: '已归档',
  ingested: '已入库',
  synced: '已同步',
  failed: '失败',
}

const sourceLabels = {
  wechat: '微信',
  'web-manual': '网页手动',
  feishu: '飞书',
  api: 'API',
  'quick-note': '快速速记',
}

const purposeLabels = {
  quick_capture: '快速捕获',
  study_doc: '学习文档',
  work_doc: '工作文档',
  life_record: '生活记录',
}

function fetchData() {
  loading.value = true
  Promise.all([
    getMessageStats().catch(() => null),
    getCategoryStats().catch(() => null),
  ])
    .then(([statsRes, catRes]) => {
      if (statsRes) {
        const { data, errorCode } = statsRes.data
        if (errorCode === 2001) {
          router.push('/login')
          return
        }
        if (errorCode === 200 && data) {
          stats.value = data
        }
      }
      if (catRes) {
        const { data, errorCode } = catRes.data
        if (errorCode === 200 && data) {
          categoryData.value = data
        }
      }
      nextTick(() => {
        renderCharts()
      })
    })
    .finally(() => {
      loading.value = false
    })
}

function renderCharts() {
  renderSourceChart()
  renderPurposeChart()
  renderCategoryChart()
}

function renderSourceChart() {
  if (!sourceChartRef.value || !stats.value?.bySource) return
  if (sourceChart) sourceChart.dispose()
  sourceChart = echarts.init(sourceChartRef.value)
  const bySource = stats.value.bySource
  const data = Object.entries(bySource).map(([key, value]) => ({
    name: sourceLabels[key] || key,
    value: value,
  }))
  sourceChart.setOption({
    title: { text: '来源分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{c}' },
        data: data,
      },
    ],
  })
}

function renderPurposeChart() {
  if (!purposeChartRef.value || !stats.value?.byPurpose) return
  if (purposeChart) purposeChart.dispose()
  purposeChart = echarts.init(purposeChartRef.value)
  const byPurpose = stats.value.byPurpose
  const categories = Object.keys(byPurpose)
  const values = Object.values(byPurpose)
  purposeChart.setOption({
    title: { text: '内容用途分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: categories.map((k) => purposeLabels[k] || k),
      axisLabel: { rotate: 30 },
    },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: values,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#1890ff' },
            { offset: 1, color: '#69c0ff' },
          ]),
          borderRadius: [4, 4, 0, 0],
        },
      },
    ],
    grid: { left: '10%', right: '10%', bottom: '20%', top: '15%' },
  })
}

function renderCategoryChart() {
  if (!categoryChartRef.value || !categoryData.value?.categories) return
  if (categoryChart) categoryChart.dispose()
  categoryChart = echarts.init(categoryChartRef.value)
  const cats = categoryData.value.categories
  const names = Object.keys(cats)
  const values = Object.values(cats)
  categoryChart.setOption({
    title: { text: '内容分类分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { rotate: 30 },
    },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: values,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#722ed1' },
            { offset: 1, color: '#b37feb' },
          ]),
          borderRadius: [4, 4, 0, 0],
        },
      },
    ],
    grid: { left: '10%', right: '10%', bottom: '20%', top: '15%' },
  })
}

function handleResize() {
  sourceChart?.resize()
  purposeChart?.resize()
  categoryChart?.resize()
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  sourceChart?.dispose()
  purposeChart?.dispose()
  categoryChart?.dispose()
})
</script>

<template>
  <div class="tab-stats">
    <Spin :spinning="loading">
      <!-- 消息量总览卡片 -->
      <div v-if="stats?.byStatus" class="stat-cards">
        <Card
          v-for="(count, status) in stats.byStatus"
          :key="status"
          size="small"
          class="stat-card"
        >
          <div class="stat-number">{{ count }}</div>
          <div class="stat-label">{{ statusLabels[status] || status }}</div>
        </Card>
        <Card size="small" class="stat-card stat-card-total">
          <div class="stat-number">{{ stats.total || 0 }}</div>
          <div class="stat-label">总消息量</div>
        </Card>
      </div>

      <!-- 图表区域 -->
      <div class="charts-grid">
        <div class="chart-container">
          <div ref="sourceChartRef" class="chart-box"></div>
        </div>
        <div class="chart-container">
          <div ref="purposeChartRef" class="chart-box"></div>
        </div>
        <div class="chart-container chart-container-wide">
          <div ref="categoryChartRef" class="chart-box"></div>
        </div>
      </div>

      <!-- 标签云 -->
      <div v-if="categoryData?.tags && categoryData.tags.length > 0" class="tags-section">
        <Card title="标签云" size="small">
          <div class="tag-cloud">
            <Tag
              v-for="tag in categoryData.tags"
              :key="tag"
              color="blue"
              class="cloud-tag"
            >
              {{ tag }}
            </Tag>
          </div>
        </Card>
      </div>

      <Empty v-if="!loading && !stats" description="暂无统计数据" />
    </Spin>
  </div>
</template>

<style scoped>
.tab-stats {
  padding-top: 8px;
}

.stat-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  min-width: 120px;
  text-align: center;
  border-radius: 8px;
}

.stat-card-total {
  border-color: #1890ff;
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  color: #1890ff;
  line-height: 1.2;
}

.stat-card-total .stat-number {
  color: #1890ff;
}

.stat-label {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.chart-container {
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
}

.chart-container-wide {
  grid-column: span 2;
}

.chart-box {
  width: 100%;
  height: 320px;
}

.tags-section {
  margin-bottom: 24px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cloud-tag {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 12px;
  cursor: default;
}
</style>
