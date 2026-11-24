import { http } from './http'

export interface DocumentItem {
  docId: string
  name: string
  mimeType: string
  chunkCount: number
  size: number
  ingestedAt: string
}

export interface ChunkItem {
  chunkId: string
  index: number
  outlinePath: string[]
  content: string
  embeddingText: string
  charCount: number
}

export interface UploadResult {
  docId: string
  docName: string
  chunkCount: number
  skipped: boolean
  timings: { detectMs: number; parseMs: number; chunkMs: number; indexMs: number }
}

export async function listDocuments(): Promise<DocumentItem[]> {
  const { data } = await http.get('/rag/documents')
  return data.documents ?? []
}

export async function listChunks(docId: string): Promise<ChunkItem[]> {
  const { data } = await http.get(`/rag/documents/${encodeURIComponent(docId)}/chunks`)
  return data.chunks ?? []
}

export async function deleteDocument(docId: string): Promise<void> {
  await http.delete(`/rag/documents/${encodeURIComponent(docId)}`)
}

export async function batchDeleteDocuments(docIds: string[]): Promise<number> {
  const { data } = await http.post('/rag/documents/batch-delete', { docIds })
  return data.deleted ?? 0
}

export async function createChunk(docId: string, content: string): Promise<ChunkItem> {
  const { data } = await http.post(`/rag/documents/${encodeURIComponent(docId)}/chunks`, { content })
  return data
}

export async function updateChunk(docId: string, chunkId: string, content: string): Promise<ChunkItem> {
  const { data } = await http.patch(
    `/rag/documents/${encodeURIComponent(docId)}/chunks/${encodeURIComponent(chunkId)}`,
    { content }
  )
  return data
}

export async function deleteChunk(docId: string, chunkId: string): Promise<void> {
  await http.delete(`/rag/documents/${encodeURIComponent(docId)}/chunks/${encodeURIComponent(chunkId)}`)
}

export async function batchDeleteChunks(docId: string, chunkIds: string[]): Promise<number> {
  const { data } = await http.post(
    `/rag/documents/${encodeURIComponent(docId)}/chunks/batch-delete`,
    { chunkIds }
  )
  return data.deleted ?? 0
}

/**
 * 上传走 XHR 而不是 axios：需要 upload.onprogress 才能拿到真实上传进度。
 */
export function uploadDocument(file: File, onProgress?: (percent: number) => void): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/rag/documents')

    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable || !onProgress) return
      onProgress(Math.min(99, Math.round((event.loaded / event.total) * 100)))
    }

    xhr.onload = () => {
      let body: any = {}
      try {
        body = JSON.parse(xhr.responseText || '{}')
      } catch {
        /* 非 JSON 响应交给下面的分支处理 */
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress?.(100)
        resolve(body as UploadResult)
      } else {
        reject(new Error(body.error || `上传失败 HTTP ${xhr.status}`))
      }
    }
    xhr.onerror = () => reject(new Error('网络错误，上传中断'))

    const form = new FormData()
    form.append('file', file)
    xhr.send(form)
  })
}
