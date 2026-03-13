<template>
  <div class="home-page">
    <a-card class="hero-card" :bordered="false">
      <div class="hero-content">
        <div>
          <div class="hero-badge">Unified Entry</div>
          <h1>个人中枢统一入口</h1>
          <p>
            登录后的默认落点统一收敛到这里，先明确当前环境启用的业务域，
            再按需进入智能中枢或 AI 硬件服务，避免回到旧的单域默认入口。
          </p>
        </div>

        <div class="hero-tags">
          <a-tag color="blue">共享承接页</a-tag>
          <a-tag color="green">域路由已归一</a-tag>
          <a-tag color="purple">入口可按配置切换</a-tag>
        </div>
      </div>
    </a-card>

    <a-alert
      class="status-alert"
      type="info"
      show-icon
      message="当前默认策略"
      :description="defaultStrategy"
    />

    <a-row :gutter="[16, 16]" class="domain-grid">
      <a-col :xs="24" :lg="12" v-for="card in domainCards" :key="card.key">
        <a-card class="domain-card" :bordered="false">
          <template #title>
            <div class="domain-title">
              <span>{{ card.title }}</span>
              <a-tag :color="card.enabled ? 'success' : 'default'">
                {{ card.enabled ? '已启用' : '未启用' }}
              </a-tag>
            </div>
          </template>

          <p class="domain-description">{{ card.description }}</p>

          <div class="domain-meta">
            <div>
              <span class="label">菜单分组</span>
              <span class="value">{{ card.menuGroup }}</span>
            </div>
            <div>
              <span class="label">推荐入口</span>
              <span class="value mono">{{ card.target }}</span>
            </div>
          </div>

          <div class="domain-actions">
            <a-button type="primary" :disabled="!card.enabled" @click="go(card.target)">
              进入{{ card.title }}
            </a-button>
            <a-button :disabled="!card.enabled" @click="go(card.quickLinks[0].path)">
              快速打开
            </a-button>
          </div>

          <div class="quick-links">
            <div class="quick-links-title">域内快捷入口</div>
            <a-space wrap>
              <a-button
                v-for="link in card.quickLinks"
                :key="link.path"
                size="small"
                :disabled="!card.enabled"
                @click="go(link.path)"
              >
                {{ link.label }}
              </a-button>
            </a-space>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card class="shared-card" :bordered="false" title="共享入口">
      <div class="shared-actions">
        <a-button v-for="action in sharedActions" :key="action.path" @click="go(action.path)">
          {{ action.label }}
        </a-button>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { resolveDomainEntry, resolveTargetPath } from '@/utils/domain'

const router = useRouter()
const store = useStore()

const domainState = computed(() => store.state.domain || {})

const domainCards = computed(() => [
  {
    key: 'hub',
    title: '智能中枢',
    description: '承接微信收集、速记归档、知识图谱、记忆与同步主链路。',
    enabled: !!domainState.value.hub?.enabled,
    menuGroup: domainState.value.hub?.menuGroup || 'hub',
    target: resolveDomainEntry(domainState.value, 'hub'),
    quickLinks: [
      { label: '知识库配置', path: '/productKnowledge' },
      { label: '知识图谱', path: '/knowledgeGraphic' },
      { label: '长期记忆', path: '/agentLongMemory' },
    ],
  },
  {
    key: 'iot',
    title: 'AI 硬件服务',
    description: '保留设备、物模型、OTA、监控和硬件侧能力的并行入口。',
    enabled: !!domainState.value.iot?.enabled,
    menuGroup: domainState.value.iot?.menuGroup || 'iot',
    target: resolveDomainEntry(domainState.value, 'iot'),
    quickLinks: [
      { label: '产品配置', path: '/product' },
      { label: '设备数据', path: '/deviceData' },
      { label: '物模型', path: '/productModel' },
    ],
  },
])

const defaultStrategy = computed(() => {
  if (domainState.value.hub?.enabled) {
    return '当前环境启用了 hub，根路由与登录成功后的默认落点都会先回到统一入口，再由此切换到具体业务域。'
  }

  if (domainState.value.iot?.enabled) {
    return '当前环境未启用 hub，系统会把根路由和登录后的默认落点归一到 AI 硬件服务入口。'
  }

  return '当前未检测到可用业务域，请先检查后端 domain-config 配置。'
})

const sharedActions = [
  { label: '系统仪表盘', path: '/dashboard' },
  { label: '系统设置', path: '/setting' },
  { label: '关于项目', path: '/about' },
]

const go = (path) => {
  if (!path) return
  router.push(resolveTargetPath(domainState.value, path))
}
</script>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  padding: 24px;
  background: #f5f7fb;
}

.hero-card,
.domain-card,
.shared-card {
  border-radius: 16px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
}

.hero-card {
  margin-bottom: 16px;
  background: linear-gradient(135deg, #1d4ed8 0%, #7c3aed 100%);
  color: #fff;
}

.hero-content {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;

  h1 {
    color: #fff;
    margin-bottom: 12px;
  }

  p {
    margin: 0;
    max-width: 720px;
    line-height: 1.8;
    color: rgba(255, 255, 255, 0.88);
  }
}

.hero-badge {
  display: inline-block;
  margin-bottom: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.15);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-tags {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px;
}

.status-alert {
  margin-bottom: 16px;
}

.domain-grid {
  margin-bottom: 16px;
}

.domain-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.domain-description {
  min-height: 48px;
  color: #475569;
  line-height: 1.8;
}

.domain-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;

  .label {
    display: block;
    margin-bottom: 4px;
    font-size: 12px;
    color: #64748b;
  }

  .value {
    color: #0f172a;
    font-weight: 600;
  }

  .mono {
    font-family: ui-monospace, SFMono-Regular, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, Courier New, monospace;
  }
}

.domain-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.quick-links-title {
  margin-bottom: 10px;
  font-size: 13px;
  color: #64748b;
}

.shared-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 768px) {
  .home-page {
    padding: 16px;
  }

  .domain-meta {
    grid-template-columns: 1fr;
  }

  .domain-actions {
    flex-direction: column;
  }
}
</style>
