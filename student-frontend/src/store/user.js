import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.userInfo?.role === 'ADMIN',
    userName: (state) => state.userInfo?.realName || '',
    /** 当前用户 id（私聊 / 好友 / 归属判断用） */
    userId: (state) => state.userInfo?.userId || null,
    /** 账户余额（二手交易使用） */
    balance: (state) => {
      const v = state.userInfo?.balance
      return v === null || v === undefined ? 0 : Number(v)
    }
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.userInfo = {
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        role: data.role,
        college: data.college,
        balance: data.balance === null || data.balance === undefined ? 0 : data.balance
      }
      localStorage.setItem('token', data.token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    /** 资料更新后同步本地缓存的用户信息 */
    setUserInfo(partial) {
      this.userInfo = { ...(this.userInfo || {}), ...partial }
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    /** 余额变化后同步 store 与 localStorage，保证顶部与钱包页展示一致 */
    setBalance(balance) {
      const value = balance === null || balance === undefined ? 0 : Number(balance)
      this.userInfo = { ...(this.userInfo || {}), balance: value }
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
