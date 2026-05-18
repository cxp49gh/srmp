export function formatDateTime(raw: unknown): string {
  if (raw === null || raw === undefined || raw === '') return '-'
  const value = raw instanceof Date ? raw : new Date(String(raw).replace(' ', 'T'))
  if (Number.isNaN(value.getTime())) return String(raw)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}
