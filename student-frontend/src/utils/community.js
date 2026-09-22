/**
 * 二手交易 / 校园论坛 的枚举与展示映射（前端统一口径）
 */

// ==================== 二手商品 ====================

export const MARKET_CATEGORIES = [
  { value: 'ALL', label: '全部' },
  { value: 'TEXTBOOK', label: '教材书籍', icon: 'Reading' },
  { value: 'ELECTRONIC', label: '电子数码', icon: 'Cellphone' },
  { value: 'DAILY', label: '生活用品', icon: 'Household' },
  { value: 'SPORTS', label: '运动户外', icon: 'Basketball' },
  { value: 'OTHER', label: '其他', icon: 'More' }
]

export const MARKET_CATEGORY_LABEL = {
  TEXTBOOK: '教材书籍',
  ELECTRONIC: '电子数码',
  DAILY: '生活用品',
  SPORTS: '运动户外',
  OTHER: '其他'
}

export const ITEM_CONDITIONS = [
  { value: 'NEW', label: '全新' },
  { value: 'LIKE_NEW', label: '几乎全新' },
  { value: 'GOOD', label: '轻微使用痕迹' },
  { value: 'FAIR', label: '明显使用痕迹' }
]

export const ITEM_CONDITION_LABEL = {
  NEW: '全新',
  LIKE_NEW: '几乎全新',
  GOOD: '轻微使用痕迹',
  FAIR: '明显使用痕迹'
}

export const MARKET_STATUS_LABEL = {
  ON_SALE: '在售',
  OFF_SHELF: '已下架',
  SOLD: '已售出'
}

export const MARKET_STATUS_TYPE = {
  ON_SALE: 'success',
  OFF_SHELF: 'info',
  SOLD: 'warning'
}

// ==================== 论坛帖子 ====================

export const POST_CATEGORIES = [
  { value: 'ALL', label: '全部' },
  { value: 'CAMPUS', label: '校园动态', icon: 'School' },
  { value: 'STUDY', label: '学习交流', icon: 'Reading' },
  { value: 'LOST', label: '失物互助', icon: 'Help' },
  { value: 'HELP', label: '求助问答', icon: 'QuestionFilled' },
  { value: 'OTHER', label: '其他', icon: 'More' }
]

export const POST_CATEGORY_LABEL = {
  CAMPUS: '校园动态',
  STUDY: '学习交流',
  LOST: '失物互助',
  HELP: '求助问答',
  OTHER: '其他'
}

// ==================== 意见反馈 ====================

export const FEEDBACK_TYPES = [
  { value: 'SUGGESTION', label: '功能建议' },
  { value: 'COMPLAINT', label: '问题投诉' },
  { value: 'BUG', label: '故障反馈' },
  { value: 'OTHER', label: '其他' }
]

export const FEEDBACK_TYPE_LABEL = {
  SUGGESTION: '功能建议',
  COMPLAINT: '问题投诉',
  BUG: '故障反馈',
  OTHER: '其他'
}

export const FEEDBACK_STATUS_LABEL = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决'
}

export const FEEDBACK_STATUS_TYPE = {
  PENDING: 'warning',
  PROCESSING: 'primary',
  RESOLVED: 'success'
}

// ==================== 通用 ====================

/** 把逗号分隔的图片串拆成数组 */
export function splitImages(images) {
  return (images || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 时间格式化为「刚刚 / x 分钟前 / x 小时前 / MM-DD HH:mm」 */
export function relativeTime(value) {
  if (!value) return ''
  const t = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(t.getTime())) return String(value)
  const diff = Date.now() - t.getTime()
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 30 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / 86400000)} 天前`
  const pad = (n) => String(n).padStart(2, '0')
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())} ${pad(t.getHours())}:${pad(t.getMinutes())}`
}

/** 金额展示：¥35.50 */
export function formatMoney(v) {
  const n = Number(v ?? 0)
  return `¥${n.toFixed(2)}`
}

/** 解析头像 URL：后端返回 /uploads/xxx 相对路径，需拼上后端地址 */
export function resolveAvatarUrl(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('data:')) return url
  const base = import.meta.env.VITE_API_BASE_URL || ''
  return base + url
}
