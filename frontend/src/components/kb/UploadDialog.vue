<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDocument } from '@/api/rag'
import { formatSize } from '@/utils/format'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'done'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

type QueueStatus = 'queued' | 'uploading' | 'done' | 'error'

interface QueueItem {
  id: number
  file: File
  name: string
  size: number
  progress: number
  status: QueueStatus
  message: string
  detail: string
}

const ALLOWED = ['.md', '.markdown', '.txt', '.csv', '.pdf', '.docx']
const MAX_BYTES = 20 * 1024 * 1024

const queue = ref<QueueItem[]>([])
const running = ref(false)
const uploadRef = ref()
let seq = 0

function onChange(uploadFile: any) {
  const file: File | undefined = uploadFile?.raw
  uploadRef.value?.clearFiles()
  if (!file) return

  const ext = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
  let message = ''
  if (!ALLOWED.includes(ext)) {
    message = `不支持的格式 ${ext}，仅支持 ${ALLOWED.join(' / ')}`
  } else if (!file.size) {
    message = '文件为空'
  } else if (file.size > MAX_BYTES) {
    message = `超过 20MB 限制（${formatSize(file.size)}）`
  }

  queue.value.push({
    id: (seq += 1),
    file,
    name: file.name,
    size: file.size,
    progress: 0,
    status: message ? 'error' : 'queued',
    message,
    detail: ''
  })

  if (!message) drain()
}

async function drain() {
  if (running.value) return
  running.value = true
  for (const item of queue.value) {
    if (item.status !== 'queued') continue
    item.status = 'uploading'
    try {
      const result = await uploadDocument(item.file, (percent) => {
        item.progress = percent
      })
      item.status = 'done'
      item.detail = result.skipped
        ? `内容未变化，已跳过（复用 ${result.chunkCount} 个分块）`
        : `${result.chunkCount} 个分块 · 解析 ${result.timings.parseMs}ms / 分块 ${result.timings.chunkMs}ms / 入库 ${result.timings.indexMs}ms`
    } catch (error: any) {
      item.status = 'error'
      item.progress = 100
      item.message = error?.message || '上传失败'
    }
  }
  running.value = false

  const done = queue.value.filter((i) => i.status === 'done').length
  if (done) {
    ElMessage.success(`上传完成 ${done} 个文件`)
    emit('done')
  }
}

function statusText(item: QueueItem): string {
  if (item.status === 'queued') return '排队中'
  if (item.status === 'uploading') return item.progress < 100 ? `上传 ${item.progress}%` : '解析中…'
  if (item.status === 'done') return '完成'
  return '失败'
}

function close() {
  if (running.value) {
    ElMessage.warning('还有文件在上传，请稍候')
    return
  }
  visible.value = false
}
</script>

<template>
  <el-dialog v-model="visible" title="上传文档" width="560px" :close-on-click-modal="false">
    <p class="hint">支持 .md / .txt / .csv / .pdf / .docx，单文件 ≤ 20MB，可一次选多个文件（串行上传）</p>

    <el-upload
      ref="uploadRef"
      drag
      multiple
      :auto-upload="false"
      :show-file-list="false"
      accept=".md,.markdown,.txt,.csv,.pdf,.docx"
      :on-change="onChange"
      class="dropzone"
    >
      <div class="drop-icon">⬆</div>
      <div class="drop-main">拖拽文件到此处，或点击选择</div>
      <div class="drop-hint">上传后自动解析 → 分块 → 入库</div>
    </el-upload>

    <div v-if="queue.length" class="queue">
      <div v-for="item in queue" :key="item.id" class="queue-item">
        <div class="queue-top">
          <span class="queue-name" :title="item.name">{{ item.name }}</span>
          <span class="queue-status" :class="item.status">{{ statusText(item) }}</span>
        </div>
        <el-progress
          :percentage="item.progress"
          :stroke-width="5"
          :show-text="false"
          :status="item.status === 'error' ? 'exception' : undefined"
        />
        <div v-if="item.status === 'done'" class="queue-note ok">{{ item.detail }}</div>
        <div v-if="item.status === 'error'" class="queue-note bad">{{ item.message }}</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint { margin: 0 0 14px; font-size: 12.5px; color: #64748b; line-height: 1.6; }

.dropzone :deep(.el-upload-dragger) {
  border-radius: 12px;
  border: 2px dashed #cbd5e1;
  padding: 26px 16px;
  background: #fff;
}
.dropzone :deep(.el-upload-dragger:hover) { border-color: #6366f1; background: #f5f3ff; }

.drop-icon { font-size: 20px; color: #6366f1; }
.drop-main { margin-top: 4px; font-size: 13.5px; font-weight: 500; color: #334155; }
.drop-hint { margin-top: 4px; font-size: 11.5px; color: #94a3b8; }

.queue { margin-top: 14px; display: flex; flex-direction: column; gap: 8px; max-height: 260px; overflow-y: auto; }
.queue-item { border: 1px solid #e2e8f0; border-radius: 9px; padding: 10px 12px; }
.queue-top { display: flex; align-items: center; gap: 8px; margin-bottom: 7px; font-size: 12.5px; }
.queue-name { flex: 1; min-width: 0; color: #334155; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.queue-status { font-size: 11.5px; color: #64748b; white-space: nowrap; }
.queue-status.done { color: #059669; }
.queue-status.error { color: #dc2626; }
.queue-note { margin-top: 6px; font-size: 11.5px; line-height: 1.6; }
.queue-note.ok { color: #059669; }
.queue-note.bad { color: #dc2626; }
</style>
