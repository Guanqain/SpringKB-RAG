import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '@/views/Dashboard.vue'
import DocumentList from '@/views/DocumentList.vue'
import ImportDocument from '@/views/ImportDocument.vue'

const routes = [
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

export default router
