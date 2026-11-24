import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由分三块，和参考项目（ai-vue）一个结构：
 *   /auth/**   AuthLayout    登录、注册（不需要登录）
 *   /chat      ChatLayout    普通用户工作台
 *   /admin/**  AdminLayout   管理后台（需要管理员）
 *
 * 守卫只管"体验"：不让用户点进必然失败的页面。
 * 真正的边界在后端 AuthFilter，绕过前端守卫照样会被 401/403 拦下。
 */
const router = createRouter({
  history: createWebHistory('/app/'),
  routes: [
    { path: '/', redirect: '/chat' },

    {
      path: '/auth',
      component: () => import('@/layouts/AuthLayout.vue'),
      children: [
        { path: 'login', name: 'login', component: () => import('@/views/auth/LoginView.vue') },
        { path: 'register', name: 'register', component: () => import('@/views/auth/RegisterView.vue') }
      ]
    },

    {
      path: '/chat',
      component: () => import('@/layouts/ChatLayout.vue'),
      meta: { requiresAuth: true },
      children: [{ path: '', name: 'chat', component: () => import('@/views/chat/ChatView.vue') }]
    },

    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/admin/knowledge',
      meta: { requiresAuth: true, adminOnly: true },
      children: [
        {
          path: 'knowledge',
          name: 'kb-documents',
          component: () => import('@/views/admin/knowledge/DocumentListView.vue')
        },
        {
          path: 'knowledge/:docId/chunks',
          name: 'kb-chunks',
          component: () => import('@/views/admin/knowledge/ChunkListView.vue'),
          props: true
        }
      ]
    },

    { path: '/:pathMatch(.*)*', redirect: '/chat' }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  const isAuthPage = to.path.startsWith('/auth')

  // 没登录：受保护页面一律去登录页，并记住原来想去哪
  if (!auth.isLoggedIn) {
    if (to.meta.requiresAuth) {
      return { path: '/auth/login', query: { redirect: to.fullPath } }
    }
    return true
  }

  // 已登录还去登录/注册页：送回各自首页
  if (isAuthPage) {
    return auth.homePath
  }

  // 普通用户点后台：回对话页，而不是弹错误
  if (to.meta.adminOnly && !auth.isAdmin) {
    return '/chat'
  }

  return true
})

export default router
