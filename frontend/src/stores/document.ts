import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  deleteDocument,
  listDocuments,
  setDocumentActive,
} from '@/api/document'
import type { Document, DocumentQuery } from '@/types/document'

export const useDocumentStore = defineStore('document', () => {
  const documents = ref<Document[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(20)
  const loading = ref(false)
  const filter = ref<DocumentQuery>({})

  async function fetchDocuments() {
    loading.value = true
    try {
      const result = await listDocuments({
        ...filter.value,
        page: page.value,
        size: size.value,
      })
      documents.value = result.list
      total.value = result.total
      page.value = result.page
      size.value = result.size
    } finally {
      loading.value = false
    }
  }

  /** 切换页码（服务端分页） */
  async function goToPage(target: number) {
    page.value = Math.max(1, target)
    await fetchDocuments()
  }

  /** 修改每页条数：回到第 1 页 */
  async function changeSize(target: number) {
    size.value = target
    page.value = 1
    await fetchDocuments()
  }

  async function toggleActive(doc: Document) {
    const updated = await setDocumentActive(doc.id, !doc.active)
    const idx = documents.value.findIndex((d) => d.id === doc.id)
    if (idx >= 0) documents.value[idx] = updated
  }

  async function removeDocument(docId: string) {
    await deleteDocument(docId)
    // 当前页被删空且不是第 1 页时先回退一页，再重新拉取（后页行会前移补位）
    if (documents.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await fetchDocuments()
  }

  return {
    documents,
    total,
    page,
    size,
    loading,
    filter,
    fetchDocuments,
    goToPage,
    changeSize,
    toggleActive,
    removeDocument,
  }
})
