<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Select, Button, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import PersonaTable from './PersonaTable.vue'
import PersonaForm from './PersonaForm.vue'
import { getHubPersona } from '@/api/hubPersona'
import { getProduct } from '@/api/product'

const router = useRouter()

const products = ref([])
const productLoading = ref(true)
const currentProductId = ref(null)

const personaList = ref([])
const personaLoading = ref(false)

const formVisible = ref(false)
const editData = ref(null)

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
          fetchPersonaList()
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

function fetchPersonaList() {
  if (!currentProductId.value) return
  personaLoading.value = true
  getHubPersona({ productId: currentProductId.value })
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

function handleProductChange(value) {
  currentProductId.value = value
  fetchPersonaList()
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
  fetchProducts()
})
</script>

<template>
  <div class="persona-wrapper">
    <div class="persona-main">
      <div class="persona-header">
        <div class="header-left">
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
        <div class="header-right">
          <Button type="primary" :disabled="!currentProductId" @click="handleAdd">
            <PlusOutlined /> 新建人设
          </Button>
        </div>
      </div>
      <div class="persona-body">
        <PersonaTable
          :data-source="personaList"
          :loading="personaLoading"
          @edit="handleEdit"
          @refresh="handleRefresh"
        />
      </div>
    </div>
    <PersonaForm
      :visible="formVisible"
      :product-id="currentProductId"
      :edit-data="editData"
      @update:visible="formVisible = $event"
      @refresh="handleRefresh"
    />
  </div>
</template>

<style scoped>
.persona-wrapper {
  height: 100%;
  padding: 24px;
  background-color: #f0f2f5;
}

.persona-main {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.persona-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
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

.persona-body {
  width: 100%;
}
</style>
