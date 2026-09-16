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


/**
 * 金额格式化：¥35.50（二手交易 / 余额统一展示口径）
 * @param {number|string} v 金额
 * @returns {string}
 */
export function formatMoney(v) {
  const n = Number(v ?? 0)
  return `¥${n.toFixed(2)}`
}

/** 学生反馈类型文本 */
export const feedbackTypeMap = {
  SUGGESTION: '功能建议',
  COMPLAINT: '问题投诉',
  BUG: '故障反馈',
  OTHER: '其他'
}

/** 学生反馈状态文本 */
export const feedbackStatusMap = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决'
}

/** 学生反馈状态标签类型 */
export const feedbackStatusTypeMap = {
  PENDING: 'warning',
  PROCESSING: 'primary',
  RESOLVED: 'success'
}

/** 二手商品状态文本 */
export const marketStatusMap = {
  ON_SALE: '在售',
  OFF_SHELF: '已下架',
  SOLD: '已售出'
}

/** 二手商品状态标签类型 */
export const marketStatusTypeMap = {
  ON_SALE: 'success',
  OFF_SHELF: 'info',
  SOLD: 'warning'
}

/** 帖子分类文本 */
export const postCategoryMap = {
  CAMPUS: '校园动态',
  STUDY: '学习交流',
  LOST: '失物互助',
  HELP: '求助问答',
  OTHER: '其他'
}
