import dayjs from 'dayjs'

/** 标准日期时间格式化 */
export function formatDateTime(input?: string | number | Date | null): string {
  if (!input) return '-'
  const d = dayjs(input)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : '-'
}

export function formatDate(input?: string | number | Date | null): string {
  if (!input) return '-'
  const d = dayjs(input)
  return d.isValid() ? d.format('YYYY-MM-DD') : '-'
}
