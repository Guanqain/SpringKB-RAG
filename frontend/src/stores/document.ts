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
  const loading = ref(false)
  const filter = ref<DocumentQuery>({})

  async function fetchDocuments() {
    loading.value = true
    try {
      documents.value = await listDocuments(filter.value)
    } finally {
      loading.value = false
    }
  }

  async function toggleActive(doc: Document) {
    const updated = await setDocumentActive(doc.id, !doc.active)
    const idx = documents.value.findIndex((d) => d.id === doc.id)
    if (idx >= 0) documents.value[idx] = updated
  }

  async function removeDocument(docId: string) {
    await deleteDocument(docId)
    documents.value = documents.value.filter((d) => d.id !== docId)
  }

  return {
    documents,
    loading,
    filter,
    fetchDocuments,
    toggleActive,
    removeDocument,
  }
})
