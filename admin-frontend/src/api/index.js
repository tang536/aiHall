import request from './request'

// 认证
export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const getUserInfo = () => request.get('/auth/userinfo')
export const getCaptcha = () => request.get('/auth/captcha')

// 通知（管理页需要列表接口）
export const getNotifications = (params) => request.get('/notifications', { params })

// 管理员地点管理
export const adminGetLocations = (params) => request.get('/map/admin/locations', { params })
export const adminCreateLocation = (data) => request.post('/map/admin/locations', data)
export const adminUpdateLocation = (id, data) => request.put(`/map/admin/locations/${id}`, data)
export const adminDeleteLocation = (id) => request.delete(`/map/admin/locations/${id}`)

// 管理员
export const getDashboard = () => request.get('/admin/dashboard')
export const adminGetRepairs = (status) => request.get('/admin/repairs', { params: { status } })
export const adminUpdateRepairStatus = (id, data) => request.put(`/admin/repairs/${id}/status`, data)
export const adminGetApplications = (params) => request.get('/admin/applications', { params })
export const adminReviewApplication = (id, data) => request.put(`/admin/applications/${id}/review`, data)
export const adminCreateNotification = (data) => request.post('/admin/notifications', data)
export const adminUpdateNotification = (id, data) => request.put(`/admin/notifications/${id}`, data)
export const adminDeleteNotification = (id) => request.delete(`/admin/notifications/${id}`)
// PDF 导入通知：FormData（file/title/category/department）
export const importPdfNotification = (formData) => request.post('/admin/notifications/import-pdf', formData, { timeout: 60000 })
export const adminGetKnowledge = (category) => request.get('/admin/knowledge', { params: { category } })
export const adminCreateKnowledge = (data) => request.post('/admin/knowledge', data)
export const adminUpdateKnowledge = (id, data) => request.put(`/admin/knowledge/${id}`, data)
export const adminDeleteKnowledge = (id) => request.delete(`/admin/knowledge/${id}`)

// 系统设置
export const adminSetFocusPassword = (password) => request.put('/admin/system/focus-password', { password })

// ==================== 学生反馈管理 ====================
export const adminGetFeedbacks = (params) => request.get('/admin/feedbacks', { params })
export const adminReplyFeedback = (id, data) => request.put(`/admin/feedbacks/${id}/reply`, data)
export const adminDeleteFeedback = (id) => request.delete(`/admin/feedbacks/${id}`)

// ==================== 二手商品管理 ====================
export const adminGetMarketItems = (params) => request.get('/admin/market/items', { params })
export const adminOffShelfMarketItem = (id) => request.post(`/admin/market/items/${id}/offshelf`)
export const adminOnShelfMarketItem = (id) => request.post(`/admin/market/items/${id}/onshelf`)
export const adminDeleteMarketItem = (id) => request.delete(`/admin/market/items/${id}`)
export const adminGetMarketOrders = () => request.get('/admin/market/orders')

// ==================== 帖子管理 ====================
export const adminGetPosts = (params) => request.get('/admin/posts', { params })
export const adminDeletePost = (id) => request.delete(`/admin/posts/${id}`)
export const adminDeletePostReply = (replyId) => request.delete(`/admin/posts/replies/${replyId}`)
/** 帖子详情（含回复列表），复用学生端公开接口 */
export const adminGetPostDetail = (id) => request.get(`/posts/${id}`)
// 帖子分类管理
export const adminGetPostCategories = () => request.get('/admin/post-categories')
export const adminCreatePostCategory = (data) => request.post('/admin/post-categories', data)
export const adminUpdatePostCategory = (id, data) => request.put(`/admin/post-categories/${id}`, data)
export const adminDeletePostCategory = (id) => request.delete(`/admin/post-categories/${id}`)

// ==================== 用户管理 ====================
export const adminGetUsers = (params) => request.get('/admin/users', { params })
export const adminAdjustBalance = (id, data) => request.post(`/admin/users/${id}/balance`, data)
export const adminUpdateUserStatus = (id, data) => request.put(`/admin/users/${id}/status`, data)
