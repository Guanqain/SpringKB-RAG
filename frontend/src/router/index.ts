import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_KEY } from '@/utils/constants'
import Dashboard from '@/views/Dashboard.vue'
import DocumentList from '@/views/DocumentList.vue'
import ImportDocument from '@/views/ImportDocument.vue'
import Login from '@/views/Login.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: Login,
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    name: 'dashboard',
    component: Dashboard,
    meta: { title: '仪表盘' },
  },
  {
    path: '/documents',
    name: 'documents',
    component: DocumentList,
    meta: { title: '文档列表' },
  },
  {
    path: '/documents/import',
    name: 'import',
    component: ImportDocument,
    meta: { title: '文档导入' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 登录态守卫：未登录访问受保护页面 → 跳 /login；已登录访问 /login → 回首页
router.beforeEach((to) => {
  const loggedIn = Boolean(localStorage.getItem(TOKEN_KEY))
  if (to.meta.public) {
    return loggedIn ? { path: '/' } : true
  }
  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
