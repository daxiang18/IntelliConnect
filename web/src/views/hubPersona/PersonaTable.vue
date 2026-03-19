<script setup>
import { defineProps, defineEmits } from 'vue'
import { Table, Tag, Switch, Button, Popconfirm, message } from 'ant-design-vue'
import { EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { deleteHubPersona } from '@/api/hubPersona'

const props = defineProps({
  dataSource: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['edit', 'refresh'])

const summaryStyleMap = {
  concise: '简洁',
  detailed: '详细',
  bullet: '要点',
}

const languageMap = {
  zh: '中文',
  en: '英文',
  auto: '自动',
}

const columns = [
  { title: '人设名称', dataIndex: 'personaName', key: 'personaName', width: 150 },
  { title: '摘要风格', dataIndex: 'summaryStyle', key: 'summaryStyle', width: 100 },
  { title: '输出语言', dataIndex: 'language', key: 'language', width: 100 },
  { title: '最大标签数', dataIndex: 'maxTags', key: 'maxTags', width: 100 },
  { title: '最大实体数', dataIndex: 'maxEntities', key: 'maxEntities', width: 100 },
  { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 80 },
  { title: '操作', key: 'actions', width: 150, fixed: 'right' },
]

function handleEdit(record) {
  emit('edit', record)
}

function handleDelete(id) {
  deleteHubPersona({ id })
    .then((res) => {
      const { errorCode } = res.data
      if (errorCode === 200) {
        message.success('删除成功')
        emit('refresh')
      } else {
        message.error('删除失败')
      }
    })
    .catch(() => {
      message.error('删除失败')
    })
}
</script>

<template>
  <Table
    :columns="columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="false"
    row-key="id"
    size="middle"
    :scroll="{ x: 800 }"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'summaryStyle'">
        <Tag color="blue">{{ summaryStyleMap[record.summaryStyle] || record.summaryStyle }}</Tag>
      </template>
      <template v-else-if="column.key === 'language'">
        <Tag color="green">{{ languageMap[record.language] || record.language }}</Tag>
      </template>
      <template v-else-if="column.key === 'enabled'">
        <Tag :color="record.enabled ? 'green' : 'red'">{{ record.enabled ? '启用' : '停用' }}</Tag>
      </template>
      <template v-else-if="column.key === 'actions'">
        <Button type="link" size="small" @click="handleEdit(record)">
          <EditOutlined /> 编辑
        </Button>
        <Popconfirm title="确定删除此人设？" ok-text="确认" cancel-text="取消" @confirm="handleDelete(record.id)">
          <Button type="link" size="small" danger>
            <DeleteOutlined /> 删除
          </Button>
        </Popconfirm>
      </template>
    </template>
  </Table>
</template>
