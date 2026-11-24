<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { chat, vision } from '@/api/chat'
import { useChatStore } from '@/stores/chat'

marked.setOptions({ gfm: true, breaks: true })

interface ChatMessage {
  id: number
  role: 'user' | 'bot' | 'error'
  text: string
  image?: string
}

const store = useChatStore()
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const attached = ref<{ file: File; url: string } | null>(null)
const listEl = ref<HTMLElement | null>(null)
let seq = 0

function welcome() {
  messages.value = [{ id: (seq += 1), role: 'bot', text: '你好，我是知识问答助手，有什么可以帮你的吗？' }]
}
welcome()

watch(
  () => store.resetToken,
  () => {
    welcome()
    input.value = ''
    attached.value = null
  }
)

/** 先消毒再渲染，防止模型输出里的 HTML/脚本注入 */
function renderMarkdown(text: string): string {
  return DOMPurify.sanitize(marked.parse(text || '') as string)
}

async function scrollToBottom() {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

function onPickImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  attached.value = { file, url: URL.createObjectURL(file) }
  ;(event.target as HTMLInputElement).value = ''
}

function onPaste(event: ClipboardEvent) {
  const item = Array.from(event.clipboardData?.items || []).find((i) => i.type.startsWith('image/'))
  const file = item?.getAsFile()
  if (!file) return
  attached.value = { file, url: URL.createObjectURL(file) }
}

function clearImage() {
  if (attached.value) URL.revokeObjectURL(attached.value.url)
  attached.value = null
}

async function send() {
  const text = input.value.trim()
  if (sending.value) return
  if (!text && !attached.value) return

  const image = attached.value
  messages.value.push({ id: (seq += 1), role: 'user', text, image: image?.url })
  input.value = ''
  clearImage()
  sending.value = true
  await scrollToBottom()

  const typing: ChatMessage = { id: (seq += 1), role: 'bot', text: '思考中' }
  messages.value.push(typing)
  await scrollToBottom()

  try {
    const reply = image
      ? await vision(image.file, text, store.currentModel)
      : await chat(text, store.currentModel)
    typing.text = reply || '（模型返回了空内容）'
  } catch (error: any) {
    messages.value.splice(messages.value.indexOf(typing), 1)
    messages.value.push({ id: (seq += 1), role: 'error', text: error?.message || '请求失败' })
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}
</script>

<template>
  <div class="chat-view">
    <div ref="listEl" class="chat-list">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="bubble"
        :class="[msg.role, { typing: msg.role === 'bot' && msg.text === '思考中' }]"
      >
        <img v-if="msg.image" :src="msg.image" class="bubble-img" alt="uploaded" />
        <div v-if="msg.role === 'bot'" class="md" v-html="renderMarkdown(msg.text)"></div>
        <template v-else>{{ msg.text }}</template>
      </div>
    </div>

    <div class="composer">
      <div v-if="attached" class="attached-bar">
        <img :src="attached.url" alt="preview" />
        <button class="remove" @click="clearImage()">×</button>
      </div>

      <div class="composer-row">
        <label class="upload-btn" title="上传图片">
          📎
          <input type="file" accept="image/*" style="display: none" @change="onPickImage" />
        </label>
        <textarea
          v-model="input"
          rows="1"
          placeholder="请输入消息，Enter 发送，Shift+Enter 换行（可 Ctrl+V 贴图）"
          @keydown="onKeydown"
          @paste="onPaste"
        ></textarea>
        <button class="send-btn" :disabled="sending" @click="send()">
          {{ sending ? '发送中' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-view { display: flex; flex-direction: column; height: 100%; }

.chat-list {
  flex: 1; overflow-y: auto; padding: 24px 32px;
  display: flex; flex-direction: column; gap: 16px;
}

.bubble {
  max-width: min(880px, 86%); padding: 12px 16px; border-radius: 12px;
  font-size: 16px; line-height: 28px; word-break: break-word; white-space: pre-wrap;
}

.bubble.user {
  align-self: flex-end; background: #165dff; color: #fff;
  border-bottom-right-radius: 4px; max-width: 75%;
}

.bubble.bot {
  align-self: flex-start; background: #fff; color: #0f1115;
  border: 1px solid #e5e6eb; border-bottom-left-radius: 4px; white-space: normal;
}

.bubble.error {
  align-self: flex-start; background: #ffece8; color: #f53f3f;
  border: 1px solid #ffccc7; font-size: 14px;
}

.bubble-img { max-width: 180px; border-radius: 8px; margin-bottom: 8px; display: block; }

.typing::after { content: '...'; animation: dots 1.4s steps(4, end) infinite; }
@keyframes dots {
  0%, 20% { content: ''; }
  40% { content: '.'; }
  60% { content: '..'; }
  80%, 100% { content: '...'; }
}

.md :deep(p) { margin: 12px 0; }
.md :deep(p:first-child) { margin-top: 0; }
.md :deep(p:last-child) { margin-bottom: 0; }
.md :deep(h1), .md :deep(h2), .md :deep(h3), .md :deep(h4) { margin: 24px 0 12px; line-height: 1.4; }
.md :deep(ul), .md :deep(ol) { margin: 12px 0; padding-left: 22px; }
.md :deep(li) { margin: 4px 0; }
.md :deep(code) {
  font: 400 14px/22px Menlo, Monaco, Consolas, monospace;
  background: rgba(0, 0, 0, 0.06); border-radius: 4px; padding: 2px 5px;
}
.md :deep(pre) {
  background: #f9fafb; border: 1px solid rgba(0, 0, 0, 0.06); border-radius: 12px;
  padding: 16px; overflow-x: auto; margin: 16px 0;
}
.md :deep(pre code) { background: none; padding: 0; font-size: 13px; line-height: 22px; }
.md :deep(table) { border-collapse: collapse; margin: 16px 0; width: 100%; }
.md :deep(th), .md :deep(td) {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1); padding: 8px 12px; text-align: left;
}
.md :deep(blockquote) {
  margin: 16px 0; padding-left: 14px; border-left: 2px solid #adb2b8; color: #4e5969;
}
.md :deep(a) { color: #3964fe; text-decoration: none; }
.md :deep(a:hover) { text-decoration: underline; }

.composer { border-top: 1px solid #e5e6eb; background: #fff; padding: 16px 32px 24px; }

.attached-bar { max-width: 900px; margin: 0 auto 10px; position: relative; width: fit-content; }
.attached-bar img { width: 80px; height: 80px; object-fit: cover; border-radius: 6px; border: 1px solid #e5e6eb; }
.attached-bar .remove {
  position: absolute; top: -6px; right: -6px; width: 20px; height: 20px;
  border: none; border-radius: 50%; background: #f53f3f; color: #fff;
  font-size: 12px; line-height: 1; cursor: pointer;
}

.composer-row { display: flex; gap: 8px; max-width: 900px; margin: 0 auto; align-items: flex-end; }

.upload-btn {
  width: 40px; height: 44px; flex-shrink: 0; background: #f2f3f5; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; cursor: pointer; transition: background 0.2s;
}
.upload-btn:hover { background: #e5e6eb; }

.composer textarea {
  flex: 1; padding: 12px 16px; border: 1px solid #e5e6eb; border-radius: 10px;
  resize: none; font-size: 14px; font-family: inherit; line-height: 1.5;
  outline: none; min-height: 44px; max-height: 160px;
}
.composer textarea:focus { border-color: #165dff; }

.send-btn {
  width: 80px; height: 44px; flex-shrink: 0; border: none; border-radius: 10px;
  background: #165dff; color: #fff; font-size: 14px; font-weight: 500;
  cursor: pointer; transition: background 0.2s;
}
.send-btn:hover:not(:disabled) { background: #0e4bcc; }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
