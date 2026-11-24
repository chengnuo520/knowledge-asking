<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDeleteChunks,
  createChunk,
  deleteChunk,
  listChunks,
  updateChunk,
  type ChunkItem
} from '@/api/rag'
import { useKnowledgeStore } from '@/stores/knowledge'

const props = defineProps<{ docId: string }>()
const router = useRouter()
const store = useKnowledgeStore()

const CHUNK_WARN = 1024 // 对齐 application.yml 的 rag.chunk.max-chars

const chunks = ref<ChunkItem[]>([])
const loading = ref(false)
const keyword = ref('')
const expanded = ref<Set<string>>(new Set())
const selected = ref<string[]>([])

const editVisible = ref(false)
const editTarget = ref<ChunkItem | null>(null)
const editContent = ref('')
const createVisible = ref(false)
const createContent = ref('')
const saving = ref(false)

const doc = computed(() => store.docs.find((d) => d.docId === props.docId))

async function load() {
  loading.value = true
  expanded.value = new Set()
  selected.value = []
  try {
    chunks.value = await listChunks(props.docId)
  } catch {
    chunks.value = []
  } finally {
    loading.value = false
  }
}

function refreshAll() {
  load()
  store.load(true).catch(() => {})
}

onMounted(() => {
  if (!store.loaded) store.load().catch(() => {})
  load()
})
watch(() => props.docId, load)

const visible = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return chunks.value
  return chunks.value.filter((chunk) => {
    const path = (chunk.outlinePath || []).join(' › ').toLowerCase()
    return (chunk.content || '').toLowerCase().includes(kw) || path.includes(kw)
  })
})

const stats = computed(() => {
  const chars = chunks.value.map((c) => Number(c.charCount) || (c.content || '').length)
  const sum = chars.reduce((a, b) => a + b, 0)
  const max = chars.length ? Math.max(...chars) : 0
  const over = chars.filter((n) => n > CHUNK_WARN).length
  return [
    { icon: '🧩', label: '总块数', value: String(chunks.value.length), scope: '本文件' },
    {
      icon: '📏',
      label: '平均字符',
      value: chars.length ? String(Math.round(sum / chars.length)) : '0',
      scope: 'per chunk'
    },
    { icon: '📈', label: '最大块', value: String(max), scope: '字符' },
    { icon: '⚠️', label: '超预算块', value: String(over), scope: `> ${CHUNK_WARN}` }
  ]
})

const allExpanded = computed(
  () => visible.value.length > 0 && visible.value.every((c) => expanded.value.has(c.chunkId))
)

const allSelected = computed(
  () => visible.value.length > 0 && visible.value.every((c) => selected.value.includes(c.chunkId))
)
const indeterminate = computed(() => selected.value.length > 0 && !allSelected.value)

function toggle(chunkId: string) {
  const next = new Set(expanded.value)
  if (next.has(chunkId)) next.delete(chunkId)
  else next.add(chunkId)
  expanded.value = next
}

function toggleAll() {
  if (allExpanded.value) {
    expanded.value = new Set()
    return
  }
  expanded.value = new Set(visible.value.map((c) => c.chunkId))
}

function toggleSelect(chunkId: string) {
  selected.value = selected.value.includes(chunkId)
    ? selected.value.filter((id) => id !== chunkId)
    : [...selected.value, chunkId]
}

function toggleSelectAll(checked: boolean) {
  selected.value = checked ? visible.value.map((c) => c.chunkId) : []
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text || '')
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function pathOf(chunk: ChunkItem): string {
  return chunk.outlinePath?.length ? chunk.outlinePath.join(' › ') : '（无标题路径）'
}

/* ---------- 编辑 ---------- */
function openEdit(chunk: ChunkItem) {
  editTarget.value = chunk
  editContent.value = chunk.content
  editVisible.value = true
}

async function saveEdit() {
  if (!editTarget.value) return
  if (!editContent.value.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  saving.value = true
  try {
    await updateChunk(props.docId, editTarget.value.chunkId, editContent.value)
    ElMessage.success('已保存，旧向量已清空')
    editVisible.value = false
    refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ---------- 新建 ---------- */
function openCreate() {
  createContent.value = ''
  createVisible.value = true
}

async function saveCreate() {
  if (!createContent.value.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  saving.value = true
  try {
    await createChunk(props.docId, createContent.value)
    ElMessage.success('已追加到末尾')
    createVisible.value = false
    refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ---------- 删除 ---------- */
async function removeOne(chunk: ChunkItem) {
  try {
    await ElMessageBox.confirm(
      `将删除第 ${chunk.index} 块（${chunk.charCount} 字），不可恢复。`,
      '确认删除分块？',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteChunk(props.docId, chunk.chunkId)
    ElMessage.success('已删除')
    refreshAll()
  } catch {
    /* 拦截器已提示 */
  }
}

async function removeSelected() {
  if (!selected.value.length) return
  try {
    await ElMessageBox.confirm(
      `将删除选中的 ${selected.value.length} 个分块，不可恢复。`,
      '确认批量删除？',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const deleted = await batchDeleteChunks(props.docId, selected.value)
    ElMessage.success(`已删除 ${deleted} 个分块`)
    refreshAll()
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <div class="admin-page">
    <div class="breadcrumb">
      <button class="link" @click="router.push('/admin/knowledge')">文档列表</button>
      <span class="sep">/</span>
      <span class="current">{{ doc?.name || docId }}</span>
    </div>

    <div class="admin-page-header">
      <div>
        <h1 class="admin-page-title">分块管理</h1>
        <p class="admin-page-subtitle">
          {{ doc?.name || docId }} · 可编辑、新建、删除；改动内容会清空该块的向量，等重新嵌入
        </p>
      </div>
      <div class="admin-page-actions">
        <el-input v-model="keyword" placeholder="过滤块内容或标题路径" clearable style="width: 210px" />
        <el-button @click="load">刷新</el-button>
        <el-button @click="toggleAll">{{ allExpanded ? '全部收起' : '全部展开' }}</el-button>
        <el-button type="primary" @click="openCreate">新建分块</el-button>
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
        <span class="admin-stat-scope">{{ card.scope }}</span>
      </div>
    </div>

    <div class="admin-card" v-loading="loading">
      <div class="admin-card-head">
        <div>
          <div class="admin-card-title">分块列表</div>
          <div class="admin-card-desc">
            {{ keyword ? `匹配 ${visible.length} / ${chunks.length} 块` : `共 ${chunks.length} 块` }}
          </div>
        </div>
        <div class="head-tools">
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="indeterminate"
            :disabled="!visible.length"
            @change="toggleSelectAll"
          >
            全选
          </el-checkbox>
          <span class="sel-info">已选 {{ selected.length }}</span>
          <el-button size="small" type="danger" plain :disabled="!selected.length" @click="removeSelected">
            批量删除
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && chunks.length === 0" description="该文档没有分块" :image-size="80" />

      <div v-else-if="visible.length === 0" class="empty-line">没有包含「{{ keyword }}」的分块</div>

      <div v-else class="chunk-list">
        <div
          v-for="chunk in visible"
          :key="chunk.chunkId"
          class="chunk"
          :class="{ expanded: expanded.has(chunk.chunkId) }"
        >
          <div class="chunk-head">
            <el-checkbox
              :model-value="selected.includes(chunk.chunkId)"
              @change="toggleSelect(chunk.chunkId)"
            />
            <span class="chunk-idx" @click="toggle(chunk.chunkId)">#{{ chunk.index }}</span>
            <span class="chunk-path" :title="pathOf(chunk)" @click="toggle(chunk.chunkId)">
              {{ pathOf(chunk) }}
            </span>
            <span class="chunk-chars" :class="{ warn: chunk.charCount > CHUNK_WARN }">
              {{ chunk.charCount }} 字
            </span>
            <el-button link type="primary" size="small" @click.stop="openEdit(chunk)">编辑</el-button>
            <el-button link size="small" @click.stop="copy(chunk.content)">复制</el-button>
            <el-button link type="danger" size="small" @click.stop="removeOne(chunk)">删除</el-button>
          </div>
          <div class="chunk-body" @click="toggle(chunk.chunkId)">{{ chunk.content }}</div>
        </div>
      </div>
    </div>

    <!-- 编辑分块 -->
    <el-dialog v-model="editVisible" title="编辑分块" width="680px" :close-on-click-modal="false">
      <p class="dialog-hint">
        保存后正文与 embeddingText 一起重建，该块的 embedding 会被清空（旧向量与新正文不对应）
      </p>
      <el-input v-model="editContent" type="textarea" :rows="12" placeholder="分块内容" />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建分块 -->
    <el-dialog v-model="createVisible" title="新建分块" width="680px" :close-on-click-modal="false">
      <p class="dialog-hint">新块追加到末尾，序号自动分配；标题路径为空，检索时靠正文本身命中</p>
      <el-input v-model="createContent" type="textarea" :rows="12" placeholder="分块内容" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.breadcrumb { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #94a3b8; margin-bottom: 14px; }
.breadcrumb .link { border: none; background: none; padding: 0; color: #4f46e5; cursor: pointer; font: inherit; }
.breadcrumb .link:hover { text-decoration: underline; }
.breadcrumb .current { color: #475569; font-weight: 500; }

.head-tools { display: flex; align-items: center; gap: 10px; }
.sel-info { font-size: 12.5px; color: #64748b; }

.empty-line { padding: 48px 20px; text-align: center; color: #94a3b8; font-size: 13px; }

.chunk-list { display: flex; flex-direction: column; }
.chunk { border-bottom: 1px solid #f1f5f9; padding: 13px 20px; }
.chunk:last-child { border-bottom: none; }

.chunk-head { display: flex; align-items: center; gap: 9px; }
.chunk-idx {
  font-size: 12px; color: #94a3b8; min-width: 34px; cursor: pointer;
  font-variant-numeric: tabular-nums;
}
.chunk-path {
  flex: 1; min-width: 0; font-size: 12.5px; color: #475569; cursor: pointer;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.chunk-chars { font-size: 11.5px; color: #94a3b8; font-variant-numeric: tabular-nums; white-space: nowrap; }
.chunk-chars.warn {
  color: #b45309; background: #fffbeb; border: 1px solid #fde68a;
  border-radius: 999px; padding: 1px 7px;
}

.chunk-body {
  margin-top: 9px; font-size: 13px; line-height: 1.75; color: #334155;
  white-space: pre-wrap; word-break: break-word; cursor: pointer;
  background: #f8fafc; border: 1px solid #eef2f7; border-radius: 9px;
  padding: 11px 13px; max-height: 84px; overflow: hidden;
}
.chunk.expanded .chunk-body { max-height: none; }

.dialog-hint { margin: 0 0 12px; font-size: 12.5px; color: #64748b; line-height: 1.6; }
</style>
