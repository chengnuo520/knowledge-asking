<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useKnowledgeStore } from '@/stores/knowledge'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()
const knowledge = useKnowledgeStore()

onMounted(async () => {
  await chat.loadModels()
  // 知识库列表是 ADMIN 专属接口，普通用户请求必然 403，不如不请求
  if (auth.isAdmin) {
    try {
      await knowledge.load()
    } catch {
      /* 数据库没起也不该挡住聊天 */
    }
  }
})

async function signOut() {
  await auth.signOut()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="chat-shell">
    <aside class="chat-sidebar">
      <div class="avatar">QA</div>
      <div class="bot-name">知识问答助手</div>

      <div class="model-wrap">
        <label>当前模型</label>
        <el-select v-model="chat.currentModel" style="width: 100%">
          <el-option v-for="m in chat.models" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </div>

      <button class="side-btn" @click="chat.newSession()">+ 新会话</button>
      <button v-if="auth.isAdmin" class="side-btn" @click="router.push('/admin/knowledge')">
        <span>📚 知识库</span>
        <span v-if="knowledge.docs.length" class="side-badge">{{ knowledge.docs.length }}</span>
      </button>

      <div class="side-user">
        <div class="side-user-main">
          <div class="side-avatar">{{ auth.displayName.slice(0, 1) }}</div>
          <div class="side-user-meta">
            <div class="side-user-name">{{ auth.displayName }}</div>
            <div class="side-user-role">{{ auth.isAdmin ? '管理员' : '普通用户' }}</div>
          </div>
        </div>
        <button class="side-logout" @click="signOut">退出</button>
      </div>

      <div class="side-foot">AgentScope · Spring Boot 3</div>
    </aside>

    <main class="chat-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.chat-shell { display: flex; height: 100%; overflow: hidden; }

.chat-sidebar {
  width: 240px; flex-shrink: 0; background: #fff; border-right: 1px solid #e5e6eb;
  padding: 24px 16px; display: flex; flex-direction: column; gap: 14px;
}

.avatar {
  width: 80px; height: 80px; border-radius: 50%; margin: 0 auto;
  background: linear-gradient(135deg, #667eea, #764ba2); color: #fff;
  font-size: 32px; font-weight: 600; display: flex; align-items: center; justify-content: center;
}

.bot-name { text-align: center; font-size: 15px; font-weight: 600; color: #1d2129; }
.model-wrap { display: flex; flex-direction: column; gap: 6px; }
.model-wrap label { font-size: 12px; color: #86909c; }

.side-btn {
  width: 100%; padding: 10px 12px; background: #f2f3f5; border: none; border-radius: 8px;
  font-size: 14px; color: #4e5969; cursor: pointer; font-family: inherit;
  display: flex; align-items: center; justify-content: space-between; transition: background 0.2s;
}
.side-btn:hover { background: #e5e6eb; }

.side-badge {
  min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px;
  background: #4f46e5; color: #fff; font-size: 11px; font-weight: 600;
  display: inline-flex; align-items: center; justify-content: center;
}

.side-foot { margin-top: 2px; font-size: 11px; color: #c9cdd4; text-align: center; }

.side-user {
  margin-top: auto; padding-top: 12px; border-top: 1px solid #f0f1f3;
  display: flex; align-items: center; gap: 8px;
}

.side-user-main { display: flex; align-items: center; gap: 9px; min-width: 0; flex: 1; }

.side-avatar {
  width: 30px; height: 30px; border-radius: 9px; flex-shrink: 0;
  background: linear-gradient(135deg, #667eea, #764ba2); color: #fff;
  font-size: 13px; font-weight: 600; display: flex; align-items: center; justify-content: center;
}

.side-user-meta { min-width: 0; }
.side-user-name {
  font-size: 12.5px; color: #1d2129; font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.side-user-role { font-size: 10.5px; color: #86909c; margin-top: 1px; }

.side-logout {
  flex-shrink: 0; padding: 5px 9px; border-radius: 7px; cursor: pointer;
  border: 1px solid #e5e6eb; background: #fff; color: #86909c;
  font-size: 11.5px; font-family: inherit; transition: 0.15s;
}
.side-logout:hover { border-color: #f09595; color: #a32d2d; background: #fcebeb; }

.chat-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
</style>
