<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge'
import { fileKind, formatSize, relativeTime } from '@/utils/format'
import UploadDialog from '@/components/kb/UploadDialog.vue'
import { batchDeleteDocuments, type DocumentItem } from '@/api/rag'

const route = useRoute()
const router = useRouter()
const store = useKnowledgeStore()

const keyword = ref('')
const uploadVisible = ref(false)
const loading = ref(false)
const selectedRows = ref<DocumentItem[]>([])

async function load() {
  loading.value = true
  try {
    await store.load(true)
    selectedRows.value = []
  } catch {
    /* 错误提示已由 axios 拦截器统一弹出 */
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  if (route.query.upload === '1') uploadVisible.value = true
})

watch(uploadVisible, (open) => {
  const query = { ...route.query }
  if (open) query.upload = '1'
  else delete query.upload
  router.replace({ query })
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return store.docs
  return store.docs.filter((doc) => doc.name.toLowerCase().includes(kw))
})

const stats = computed(() => [
  { icon: '📄', label: '文档数', value: String(store.docs.length), scope: '全部' },
  { icon: '🧩', label: '分块总数', value: String(store.chunkTotal), scope: '全部' },
  { icon: '💾', label: '占用体积', value: formatSize(store.sizeTotal), scope: '原始文件' },
  {
    icon: '🕒',
    label: '最近入库',
    value: store.latest ? relativeTime(store.latest.t) : '—',
    scope: store.latest?.name ?? '暂无'
  }
])

function onSelectionChange(rows: DocumentItem[]) {
  selectedRows.value = rows
}

/** 行点击进详情，但点复选框/操作按钮时不该跳转 */
function onRowClick(row: DocumentItem, _column: unknown, event: MouseEvent) {
  const target = event?.target as HTMLElement | null
  if (target?.closest('.el-checkbox') || target?.closest('button')) return
  openChunks(row)
}

function openChunks(row: DocumentItem) {
  router.push({ name: 'kb-chunks', params: { docId: row.docId } })
}

async function remove(row: DocumentItem) {
  try {
    await ElMessageBox.confirm(
      `将删除《${row.name}》及其 ${row.chunkCount} 个分块，操作不可恢复。`,
      '确认删除文档？',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await store.remove(row.docId)
    ElMessage.success('已删除')
  } catch {
    /* 拦截器已提示 */
  }
}

async function removeSelected() {
  const rows = selectedRows.value
  if (!rows.length) return
  const chunkTotal = rows.reduce((sum, row) => sum + (Number(row.chunkCount) || 0), 0)
  try {
    await ElMessageBox.confirm(
      `将删除选中的 ${rows.length} 篇文档及其 ${chunkTotal} 个分块，操作不可恢复。`,
      '确认批量删除？',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const deleted = await batchDeleteDocuments(rows.map((row) => row.docId))
    ElMessage.success(`已删除 ${deleted} 篇文档`)
    load()
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">知识库管理</h1>
        <p class="admin-page-subtitle">上传 → 解析 → 分块 → 入库，数据来自 PostgreSQL + pgvector</p>
      </div>
      <div class="admin-page-actions">
        <el-input v-model="keyword" placeholder="搜索文档名称" clearable style="width: 190px" />
        <el-button
          v-if="selectedRows.length"
          type="danger"
          plain
          @click="removeSelected"
        >
          批量删除（{{ selectedRows.length }}）
        </el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="uploadVisible = true">上传文档</el-button>
      </div>
    </div>

    <div class="admin-stat-grid">
      <div v-for="card in stats" :key="card.label" class="admin-stat-card">
        <div class="admin-stat-main">
          <div class="admin-stat-icon">{{ card.icon }}</div>
          <div>
            <div class="admin-stat-label">{{ card.label }}</div>
            <div class="admin-stat-value">{{ card.value }}</div>
          </div>
        </div>
        <span class="admin-stat-scope" :title="card.scope">{{ card.scope }}</span>
      </div>
    </div>

    <div class="admin-card">
      <div class="admin-card-head">
        <div>
          <div class="admin-card-title">文档列表</div>
          <div class="admin-card-desc">点击任意一行查看分块结构，勾选后可批量删除</div>
        </div>
        <span class="doc-count">{{ keyword ? `${filtered.length} / ${store.docs.length}` : store.docs.length }} 篇</span>
      </div>

      <el-empty
        v-if="!loading && store.docs.length === 0"
        description="还没有文档，点右上角「上传文档」开始"
        :image-size="90"
      />

      <el-table
        v-else
        v-loading="loading"
        :data="filtered"
        style="width: 100%"
        :row-style="{ cursor: 'pointer' }"
        @selection-change="onSelectionChange"
        @row-click="onRowClick"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="文档" min-width="280">
          <template #default="{ row }">
            <div class="doc-name">{{ row.name }}</div>
            <div class="doc-meta">{{ row.mimeType }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light" disable-transitions>{{ fileKind(row.name) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="100" align="right" />
        <el-table-column label="大小" width="110" align="right">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="入库时间" width="140">
          <template #default="{ row }">{{ relativeTime(row.ingestedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openChunks(row)">查看分块</el-button>
            <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <UploadDialog v-model="uploadVisible" @done="load" />
  </div>
</template>

<style scoped>
.doc-count { font-size: 12.5px; color: #64748b; }
.doc-name { font-size: 13px; font-weight: 500; color: #0f172a; }
.doc-meta { margin-top: 2px; font-size: 11.5px; color: #94a3b8; }
</style>
