<script setup>
import { ref } from 'vue'
import { Tabs } from 'ant-design-vue'
import {
  UserOutlined,
  SettingOutlined,
  RobotOutlined,
  BarChartOutlined,
} from '@ant-design/icons-vue'
import TabPersona from './TabPersona.vue'
import TabPipeline from './TabPipeline.vue'
import TabModels from './TabModels.vue'
import TabStats from './TabStats.vue'

const activeTab = ref('persona')
</script>

<template>
  <div class="hub-settings-wrapper">
    <div class="hub-settings-main">
      <h2 class="page-title">中枢设置</h2>
      <Tabs v-model:activeKey="activeTab" size="large">
        <Tabs.TabPane key="persona">
          <template #tab>
            <span><UserOutlined /> AI 人设</span>
          </template>
          <TabPersona />
        </Tabs.TabPane>
        <Tabs.TabPane key="pipeline">
          <template #tab>
            <span><SettingOutlined /> 处理逻辑</span>
          </template>
          <TabPipeline />
        </Tabs.TabPane>
        <Tabs.TabPane key="models">
          <template #tab>
            <span><RobotOutlined /> 模型配置</span>
          </template>
          <TabModels />
        </Tabs.TabPane>
        <Tabs.TabPane key="stats">
          <template #tab>
            <span><BarChartOutlined /> 使用统计</span>
          </template>
          <TabStats />
        </Tabs.TabPane>
      </Tabs>
    </div>
  </div>
</template>

<style scoped>
.hub-settings-wrapper {
  height: 100%;
  padding: 24px;
  background-color: #f0f2f5;
  overflow-y: auto;
}

.hub-settings-main {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  min-height: 100%;
}

.page-title {
  margin: 0 0 16px 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
}
</style>
