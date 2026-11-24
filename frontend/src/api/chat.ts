import { http } from './http'

export interface ModelItem {
  id: string
  name: string
  modelName: string
}

export async function listModels(): Promise<ModelItem[]> {
  const { data } = await http.get('/models')
  return data ?? []
}

export async function chat(message: string, model: string): Promise<string> {
  const { data } = await http.post('/chat', { message, model })
  if (data.error) throw new Error(data.error)
  return data.reply ?? ''
}

export async function vision(file: File, message: string, model: string): Promise<string> {
  const form = new FormData()
  form.append('image', file)
  form.append('message', message || '描述这张图片')
  form.append('model', model)
  const { data } = await http.post('/vision', form)
  if (data.error) throw new Error(data.error)
  return data.reply ?? ''
}
