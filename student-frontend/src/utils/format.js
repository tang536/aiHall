// 通用格式化工具函数

/**
 * 格式化日期时间为 YYYY-MM-DD HH:mm
 * @param {string|Date} t 时间字符串或Date对象
 * @returns {string} 格式化后的时间字符串
 */
export function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 格式化日期为 YYYY-MM-DD
 * @param {string|Date} d 日期字符串或Date对象
 * @returns {string} 格式化后的日期字符串
 */
export function formatDate(d) {
  if (!d) return ''
  const date = new Date(d)
  if (isNaN(date.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * 数字星期转中文名称
 * @param {number} day 1-7（1=周一）
 * @returns {string} 周一 ~ 周日
 */
export function getDayName(day) {
  return ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][day - 1] || ''
}

/**
 * 报修状态文本映射
 */
export const repairStatusMap = {
  SUBMITTED: '已提交',
  ACCEPTED: '已受理',
  REPAIRING: '维修中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

/**
 * 报修状态标签类型映射
 */
export const repairStatusTypeMap = {
  SUBMITTED: 'info',
  ACCEPTED: 'warning',
  REPAIRING: 'primary',
  COMPLETED: 'success',
  CANCELLED: 'danger'
}

/**
 * 申请状态文本映射
 */
export const applicationStatusMap = {
  SUBMITTED: '已提交',
  REVIEWING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  COMPLETED: '已完成'
}

/**
 * 申请状态标签类型映射
 */
export const applicationStatusTypeMap = {
  SUBMITTED: 'info',
  REVIEWING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  COMPLETED: 'success'
}
