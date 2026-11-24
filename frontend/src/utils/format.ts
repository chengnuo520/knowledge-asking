export function formatSize(bytes?: number): string {
  const n = Number(bytes) || 0
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB']
  let value = n / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i += 1
  }
  return `${value >= 100 ? value.toFixed(0) : value.toFixed(1)} ${units[i]}`
}

export function relativeTime(value?: string | number): string {
  const t = typeof value === 'number' ? value : Date.parse(value || '')
  if (isNaN(t)) return '—'
  const diff = Date.now() - t
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 2_592_000_000) return `${Math.floor(diff / 86_400_000)} 天前`
  const d = new Date(t)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function fileKind(name?: string): string {
  const raw = String(name || '')
  const ext = raw.slice(raw.lastIndexOf('.')).toLowerCase()
  const map: Record<string, string> = {
    '.md': 'Markdown',
    '.markdown': 'Markdown',
    '.txt': 'Text',
    '.csv': 'CSV',
    '.pdf': 'PDF',
    '.docx': 'Word'
  }
  return map[ext] || '未知'
}
