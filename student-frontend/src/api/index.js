import request from './request'

// 认证
export const login = (data) => request.post('/auth/login', data)
/** 学校账号登录：绑定教务系统后，用教务系统学号+密码登录本平台 */
export const loginWithSchool = (data) => request.post('/auth/login/school', data)
export const register = (data) => request.post('/auth/register', data)
export const logout = () => request.post('/auth/logout')
export const getUserInfo = () => request.get('/auth/userinfo')
export const getCaptcha = () => request.get('/auth/captcha')

// 个人资料
export const updateProfile = (data) => request.put('/auth/profile', data)
export const updatePassword = (data) => request.put('/auth/password', data)

// 校园平台绑定
export const bindJwxt = (data) => request.post('/auth/bind/jwxt', data)
export const getBindStatus = () => request.get('/auth/bind/status')

// 课表考试
export const getCourses = () => request.get('/schedule/courses')
export const getExams = () => request.get('/schedule/exams')
export const batchSaveCourses = (courses) => request.post('/schedule/courses/batch', courses, { timeout: 30000 })
export const parseSchedule = (text) => request.post('/schedule/parse', { text }, { timeout: 120000 })
export const syncFromJwxt = () => request.post('/schedule/sync-jwxt', {}, { timeout: 60000 })

// 校园地图
export const getLocations = (config = {}) => request.get('/map/locations', config)
export const getLocationsByCategory = (category) => request.get(`/map/locations/category/${category}`)
export const searchLocations = (keyword) => request.get('/map/locations/search', { params: { keyword } })
export const getLocationDetail = (id) => request.get(`/map/locations/${id}`)
export const planRoute = (from, to) => request.get('/map/route', { params: { from, to } })

// 失物招领
export const getLostFound = (type) => request.get('/lost-found', { params: { type } })
export const getLostFoundDetail = (id) => request.get(`/lost-found/${id}`)
export const publishLostFound = (data) => request.post('/lost-found', data)
export const claimLostFound = (id) => request.post(`/lost-found/${id}/claim`)
export const smartMatch = (data) => request.post('/lost-found/match', data)

// 报修
export const submitRepair = (data) => request.post('/repair/submit', data)
export const getRepairByOrderNo = (orderNo) => request.get(`/repair/order/${orderNo}`)
export const getMyRepairs = () => request.get('/repair/my')
export const evaluateRepair = (id, data) => request.post(`/repair/${id}/evaluate`, data)

// 通知
export const getNotifications = (params, config = {}) => request.get('/notifications', { params, ...config })
export const getNotificationDetail = (id) => request.get(`/notifications/${id}`)
export const getEmergencyNotifications = () => request.get('/notifications/emergency')
// 个人通知（好友申请、商品被购买等）
export const getUserNotifications = () => request.get('/user-notifications')
export const getUserNotificationUnreadCount = () => request.get('/user-notifications/unread-count')
export const markUserNotificationRead = (id) => request.put(`/user-notifications/${id}/read`)
export const markAllUserNotificationsRead = () => request.put('/user-notifications/read-all')

// AI问答
export const sendChat = (data) => request.post('/chat/send', data, { timeout: 120000 })
export const getChatHistory = (sessionId) => request.get(`/chat/history/${sessionId}`)
export const chatFeedback = (data) => request.post('/chat/feedback', data)
export const getChatStatus = (config = {}) => request.get('/chat/status', config)

/**
 * 流式问答（SSE）：逐段回调 onDelta(text)，结束后 resolve 完整结果
 * @param {Object} data { message, sessionId, userId }
 * @param {(text:string)=>void} onDelta 每收到一段文本回调
 */
export async function streamChat(data, onDelta) {
  const token = localStorage.getItem('token')
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })
  if (!resp.ok || !resp.body) throw new Error('流式请求失败：' + resp.status)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let donePayload = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // SSE 以空行(\n\n)分隔事件
    let sep
    while ((sep = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      // 解析事件类型
      const eventLine = rawEvent.split('\n').find(l => l.startsWith('event:'))
      const eventType = eventLine ? eventLine.slice(6).trim() : 'message'
      const dataLine = rawEvent.split('\n').find(l => l.startsWith('data:'))
      if (!dataLine) continue
      let payload
      try { payload = JSON.parse(dataLine.slice(5).trim()) } catch { continue }
      // 只有 error 事件才抛异常
      if (eventType === 'error' && payload.message) {
        throw new Error(payload.message)
      }
      // delta 事件推送文本增量
      if (eventType === 'delta' && typeof payload.content === 'string' && payload.content) {
        onDelta(payload.content)
      }
      // done 事件携带完整结果
      if (eventType === 'done' && payload.answer !== undefined) {
        donePayload = payload
      }
    }
  }
  return donePayload
}

// 事项申请
export const submitApplication = (data) => request.post('/applications/submit', data)
export const getMyApplications = () => request.get('/applications/my')
export const getApplicationDetail = (appNo) => request.get(`/applications/${appNo}`)

// 公开统计（首页）
export const getPublicStats = (config = {}) => request.get('/stats/overview', config)

// 系统设置
export const verifyFocusPassword = (password) => request.post('/system/focus-password/verify', { password })
export const hasFocusPassword = () => request.get('/system/focus-password/exists')
export const initFocusPassword = (password) => request.post('/system/focus-password/init', { password })

// ==================== 图片上传（学生端） ====================
/** 上传图片，返回 { url: '/uploads/xxx.png' } */
export const uploadImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/upload/image', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

// ==================== 钱包（余额） ====================
export const getWallet = () => request.get('/wallet')
export const recharge = (amount) => request.post('/wallet/recharge', { amount })
export const getTransactions = () => request.get('/wallet/transactions')

// ==================== 二手交易平台 ====================
export const listMarket = (params) => request.get('/market', { params })
export const getMarketItem = (id) => request.get(`/market/${id}`)
export const publishMarket = (data) => request.post('/market', data)
export const updateMarket = (id, data) => request.put(`/market/${id}`, data)
export const offShelfMarket = (id) => request.post(`/market/${id}/offshelf`)
export const onShelfMarket = (id) => request.post(`/market/${id}/onshelf`)
export const deleteMarket = (id) => request.delete(`/market/${id}`)
export const buyMarket = (id) => request.post(`/market/${id}/buy`)
export const shareMarket = (id) => request.post(`/market/${id}/share`)
export const myMarketItems = () => request.get('/market/mine')
export const myBoughtOrders = () => request.get('/market/orders/mine')
export const mySoldOrders = () => request.get('/market/orders/sold')
// 收藏
export const toggleFavorite = (itemId) => request.post(`/market/favorites/${itemId}`)
export const myFavorites = () => request.get('/market/favorites')
export const checkFavorite = (itemId) => request.get(`/market/favorites/check/${itemId}`)

// ==================== 校园论坛（帖子） ====================
export const listPosts = (params) => request.get('/posts', { params })
export const getPost = (id) => request.get(`/posts/${id}`)
export const createPost = (data) => request.post('/posts', data)
export const deletePost = (id) => request.delete(`/posts/${id}`)
export const myPosts = () => request.get('/posts/mine')
export const replyPost = (id, data) => request.post(`/posts/${id}/reply`, data)
export const deleteReply = (replyId) => request.delete(`/posts/replies/${replyId}`)
export const likePost = (id) => request.post(`/posts/${id}/like`)
// 帖子收藏
export const togglePostFavorite = (postId) => request.post(`/posts/favorites/${postId}`)
export const myPostFavorites = () => request.get('/posts/favorites')
export const checkPostFavorite = (postId) => request.get(`/posts/favorites/check/${postId}`)
// 帖子分类
export const getPostCategories = () => request.get('/post-categories')

// ==================== 意见反馈 ====================
export const submitFeedback = (data) => request.post('/feedback', data)
export const myFeedback = () => request.get('/feedback/mine')
export const getFeedback = (id) => request.get(`/feedback/${id}`)
export const deleteFeedback = (id) => request.delete(`/feedback/${id}`)

// ==================== 用户 / 好友 ====================
export const searchUser = (account) => request.get('/users/search', { params: { account } })
export const getUserProfile = (id) => request.get(`/users/${id}`)
export const listFriends = () => request.get('/friends')
export const friendRequests = () => request.get('/friends/requests')
export const sentFriendRequests = () => request.get('/friends/requests/sent')
export const sendFriendRequest = (data) => request.post('/friends/requests', data)
export const acceptFriend = (id) => request.post(`/friends/requests/${id}/accept`)
export const rejectFriend = (id) => request.post(`/friends/requests/${id}/reject`)
export const deleteFriend = (userId) => request.delete(`/friends/${userId}`)

// ==================== 私聊 ====================
export const listConversations = (config = {}) => request.get('/messages/conversations', config)
export const chatHistory = (peerId) => request.get('/messages/history', { params: { peerId } })
export const sendMessage = (data) => request.post('/messages', data)
export const markConversationRead = (peerId) => request.post(`/messages/read?peerId=${peerId}`)
export const unreadCount = (config = {}) => request.get('/messages/unread-count', config)
