<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const items = [{ path: '/admin/knowledge', label: '知识库管理', icon: '📚' }]

async function signOut() {
  await auth.signOut()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="brand">
        <div class="logo">KB</div>
        <div>
          <div class="brand-title">知识库后台</div>
          <div class="brand-sub">Knowledge Admin</div>
        </div>
      </div>

      <div class="nav-group-title">内容</div>
      <nav>
        <router-link
          v-for="item in items"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path.startsWith(item.path) }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-foot">
        <div class="user-chip">
          <div class="user-avatar">{{ auth.displayName.slice(0, 1) }}</div>
          <div class="user-meta">
            <div class="user-name">{{ auth.displayName }}</div>
            <div class="user-role">管理员</div>
          </div>
        </div>
        <button class="back-btn" @click="router.push('/chat')">← 返回对话</button>
        <button class="back-btn" @click="signOut">退出登录</button>
      </div>
    </aside>

    <section class="admin-main">
      <router-view />
    </section>
  </div>
</template>

<style scoped>
.admin-shell { display: flex; height: 100%; overflow: hidden; background: #f8fafc; }

.admin-sidebar {
  width: 236px; flex-shrink: 0; display: flex; flex-direction: column;
  background: linear-gradient(180deg, #1a1f2e, #252d3d); color: rgba(255, 255, 255, 0.7);
  padding: 0 12px 16px;
}

.brand { display: flex; align-items: center; gap: 10px; padding: 22px 8px 18px; }

.logo {
  width: 38px; height: 38px; border-radius: 11px; flex-shrink: 0;
  background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff;
  font-size: 13px; font-weight: 700; display: flex; align-items: center; justify-content: center;
}

.brand-title { font-size: 13.5px; font-weight: 600; color: #fff; }
.brand-sub { font-size: 11px; color: rgba(255, 255, 255, 0.45); margin-top: 2px; }

.nav-group-title {
  padding: 10px 8px 6px; font-size: 10px; font-weight: 600;
  letter-spacing: 0.18em; text-transform: uppercase; color: rgba(255, 255, 255, 0.35);
}

.nav-item {
  display: flex; align-items: center; gap: 10px; padding: 9px 10px;
  border-radius: 8px; font-size: 13.5px; font-weight: 500;
  color: rgba(255, 255, 255, 0.7); text-decoration: none; transition: 0.15s;
}
.nav-item:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.nav-item.active { background: rgba(99, 102, 241, 0.22); color: #fff; }
.nav-icon { font-size: 14px; }

.sidebar-foot { margin-top: auto; padding-top: 16px; display: flex; flex-direction: column; gap: 8px; }

.user-chip {
  display: flex; align-items: center; gap: 9px; padding: 9px 10px; border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
}

.user-avatar {
  width: 28px; height: 28px; border-radius: 8px; flex-shrink: 0;
  background: rgba(99, 102, 241, 0.55); color: #fff; font-size: 12px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}

.user-meta { min-width: 0; }
.user-name {
  font-size: 12.5px; color: #fff; font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.user-role { font-size: 10.5px; color: rgba(255, 255, 255, 0.45); margin-top: 1px; }

.back-btn {
  width: 100%; padding: 9px 10px; border-radius: 8px; cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.14); background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.78); font-size: 12.5px; font-family: inherit; transition: 0.15s;
}
.back-btn:hover { background: rgba(255, 255, 255, 0.12); color: #fff; }

.admin-main { flex: 1; min-width: 0; overflow-y: auto; padding: 22px 28px 44px; }
</style>
