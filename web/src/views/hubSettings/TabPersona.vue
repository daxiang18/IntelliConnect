<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Button, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PersonaTable from '@/views/hubPersona/PersonaTable.vue'
import PersonaForm from '@/views/hubPersona/PersonaForm.vue'
import { getHubPersona } from '@/api/hubPersona'

const router = useRouter()

const HUB_PRODUCT_ID = 0

const personaList = ref([])
const personaLoading = ref(false)

const formVisible = ref(false)
const editData = ref(null)

function fetchPersonaList() {
  personaLoading.value = true
  getHubPersona({})
    .then((res) => {
      const { data, errorCode } = res.data
      if (errorCode === 2001) {
        router.push('/login')
        return
      }
      if (errorCode === 200) {
        personaList.value = Array.isArray(data) ? data : []
      }
    })
    .catch(() => {
      message.error('获取人设列表失败')
    })
    .finally(() => {
      personaLoading.value = false
    })
}

function handleAdd() {
  editData.value = null
  formVisible.value = true
}

function handleEdit(record) {
  editData.value = { ...record }
  formVisible.value = true
}

function handleRefresh() {
  fetchPersonaList()
}

onMounted(() => {
  fetchPersonaList()
})
</script>

<template>
  <div class="tab-persona">
    <div class="tab-header">
      <Button type="primary" @click="handleAdd">
        <PlusOutlined /> 新建人设
      </Button>
    </div>
    <div class="tab-body">
      <PersonaTable
        :data-source="personaList"
        :loading="personaLoading"
        @edit="handleEdit"
        @refresh="handleRefresh"
      />
    </div>
    <PersonaForm
      :visible="formVisible"
      :product-id="HUB_PRODUCT_ID"
      :edit-data="editData"
      @update:visible="formVisible = $event"
      @refresh="handleRefresh"
    />
  </div>
</template>

<style scoped>
.tab-persona {
  padding-top: 8px;
}

.tab-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
