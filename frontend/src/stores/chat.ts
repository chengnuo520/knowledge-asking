import { defineStore } from 'pinia'
import { listModels, type ModelItem } from '@/api/chat'

export const useChatStore = defineStore('chat', {
  state: () => ({
    models: [] as ModelItem[],
    currentModel: 'remote-deepseek',
    loaded: false,
    resetToken: 0
  }),
  actions: {
    async loadModels() {
      if (this.loaded) return
      try {
        this.models = await listModels()
        this.loaded = true
        if (!this.models.some((m) => m.id === this.currentModel)) {
          this.currentModel = this.models[0]?.id ?? this.currentModel
        }
      } catch {
        this.models = []
      }
    },
    newSession() {
      this.resetToken += 1
    }
  }
})
