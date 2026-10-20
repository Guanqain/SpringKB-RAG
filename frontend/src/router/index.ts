import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { TOKEN_KEY } from '@/utils/constants'
import Home from '@/views/Home.vue'
import QaHome from '@/views/QaHome.vue'
import KbHome from '@/views/KbHome.vue'
import KbDoc from '@/views/KbDoc.vue'
import Login from '@/views/Login.vue'
import Setup from '@/views/Setup.vue'
import Me from '@/views/Me.vue'
import Editor from '@/views/Editor.vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import AdminOverview from '@/views/admin/AdminOverview.vue'
import AdminDocuments from '@/views/admin/AdminDocuments.vue'
import AdminImport from '@/views/admin/AdminImport.vue'
import AdminMembers from '@/views/admin/AdminMembers.vue'
import AdminQaStats from '@/views/admin/AdminQaStats.vue'
import AdminBlindspots from '@/views/admin/AdminBlindspots.vue'
import AdminReview from '@/views/admin/AdminReview.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: Home,
    meta: { title: '首页' },
  },
  {
    path: '/qa',
    name: 'qa',
    component: QaHome,
    meta: { public: true, title: '智能问答' },
  },
  {
    path: '/kb',
    name: 'kb',
    component: KbHome,
    meta: { title: '知识库' },
  },
  {
    path: '/kb/doc/:id',
    name: 'kb-doc',
    component: KbDoc,
    meta: { title: '文档详情' },
  },
  {
    path: '/login',
    name: 'login',
    component: Login,
    meta: { public: true, standalone: true, title: '登录' },
  },
  {
    path: '/setup',
    name: 'setup',
    component: Setup,
    meta: { standalone: true, title: '首次启动向导' },
  },
  {
    path: '/me',
    name: 'me',
    component: Me,
    meta: { title: '个人中心' },
  },
  {
    path: '/editor',
    name: 'editor',
    component: Editor,
    meta: { title: '写文档' },
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { title: '管理后台' },
    children: [
      {
        path: '',
        name: 'admin-overview',
        component: AdminOverview,
        meta: { title: '概览' },
      },
      {
        path: 'documents',
        name: 'admin-documents',
        component: AdminDocuments,
        meta: { title: '文档管理' },
      },
      {
        path: 'import',
        name: 'admin-import',
        component: AdminImport,
        meta: { title: '数据导入' },
      },
      {
        path: 'members',
        name: 'admin-members',
        component: AdminMembers,
        meta: { title: '成员管理' },
      },
      {
        path: 'qa-stats',
        name: 'admin-qa-stats',
        component: AdminQaStats,
        meta: { title: '问答统计' },
      },
      {
        path: 'blindspots',
        name: 'admin-blindspots',
        component: AdminBlindspots,
        meta: { title: '知识盲区' },
      },
      {
        path: 'review',
        name: 'admin-review',
        component: AdminReview,
        meta: { title: '待复审' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 登录态守卫（语义同 v1）：public 白名单 = /qa、/login；已登录访问 /login 跳首页；受保护页未登录 → /login
router.beforeEach((to) => {
  const loggedIn = Boolean(localStorage.getItem(TOKEN_KEY))
  if (to.meta.public) {
    if (to.name === 'login' && loggedIn) {
      return { path: '/' }
    }
    return true
  }
  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
