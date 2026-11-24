import { defineStore } from 'pinia'
import { listDocuments, deleteDocument, type DocumentItem } from '@/api/rag'

export const useKnowledgeStore = defineStore('knowledge', {
  state: () => ({
    docs: [] as DocumentItem[],
    loading: false,
    loaded: false
  }),
  getters: {
    chunkTotal: (state) => state.docs.reduce((sum, d) => sum + (Number(d.chunkCount) || 0), 0),
    sizeTotal: (state) => state.docs.reduce((sum, d) => sum + (Number(d.size) || 0), 0),
    latest: (state) => {
      let result: { t: number; name: string } | null = null
      for (const doc of state.docs) {
        const t = Date.parse(doc.ingestedAt || '')
        if (!isNaN(t) && (!result || t > result.t)) result = { t, name: doc.name }
      }
      return result
    }
  },
  actions: {
    async load(force = false) {
      if (this.loading) return
      if (this.loaded && !force) return
      this.loading = true
      try {
        this.docs = await listDocuments()
        this.loaded = true
      } finally {
        this.loading = false
      }
    },
    async remove(docId: string) {
      await deleteDocument(docId)
      this.docs = this.docs.filter((d) => d.docId !== docId)
    }
  }
})
