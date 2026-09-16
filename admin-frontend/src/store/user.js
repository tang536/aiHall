import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.userInfo?.role === 'ADMIN',
    role: (state) => state.userInfo?.role || '',
    userName: (state) => state.userInfo?.realName || ''
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.userInfo = {
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        role: data.role,
        college: data.college
      }
      localStorage.setItem('token', data.token)
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
