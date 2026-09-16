import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'schedule',
        name: 'Schedule',
        component: () => import('@/views/Schedule.vue'),
        meta: { title: '课表考试', requiresAuth: true }
      },
      {
        path: 'map',
        name: 'CampusMap',
        component: () => import('@/views/CampusMap.vue'),
        meta: { title: '校园导航' }
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/Chat.vue'),
        meta: { title: 'AI智能问答' }
      },
      {
        path: 'lost-found',
        name: 'LostFound',
        component: () => import('@/views/LostFound.vue'),
        meta: { title: '失物招领' }
      },
      {
        path: 'repair',
        name: 'Repair',
        component: () => import('@/views/Repair.vue'),
        meta: { title: '报修服务', requiresAuth: true }
      },
      {
        path: 'applications',
        name: 'Applications',
        component: () => import('@/views/Applications.vue'),
        meta: { title: '事项申请', requiresAuth: true }
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/Notifications.vue'),
        meta: { title: '通知公告' }
      },
      {
        path: 'market',
        name: 'Market',
        component: () => import('@/views/Market.vue'),
        meta: { title: '二手交易' }
      },
      {
        path: 'market/:id',
        name: 'MarketDetail',
        component: () => import('@/views/MarketDetail.vue'),
        meta: { title: '商品详情' }
      },
      {
        path: 'posts',
        name: 'Posts',
        component: () => import('@/views/Posts.vue'),
        meta: { title: '校园论坛' }
      },
      {
        path: 'posts/:id',
        name: 'PostDetail',
        component: () => import('@/views/PostDetail.vue'),
        meta: { title: '帖子详情' }
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/Feedback.vue'),
        meta: { title: '意见反馈', requiresAuth: true }
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/Messages.vue'),
        meta: { title: '我的消息', requiresAuth: true }
      },
      {
        path: 'friends',
        name: 'Friends',
        component: () => import('@/views/Friends.vue'),
        meta: { title: '我的好友', requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人资料', requiresAuth: true }
      },
      {
        path: 'user/:id',
        name: 'UserProfile',
        component: () => import('@/views/UserProfile.vue'),
        meta: { title: '用户主页' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 检测是否为学校模式（URL参数 ?mode=school）
export function isSchoolMode() {
  const params = new URLSearchParams(window.location.search)
  return params.get('mode') === 'school'
}

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const schoolMode = isSchoolMode()
  document.title = to.meta.title ? `${to.meta.title} - AI学生事务智能办事大厅` : 'AI学生事务智能办事大厅'

  if (to.path === '/login') {
    if (userStore.isLoggedIn) {
      next('/home')
    } else {
      next()
    }
    return
  }

  // 学校模式下，需要登录的页面也可以访问（学校终端可能共用账号）
  // 但个人数据相关页面仍需登录
  if (to.meta.requiresAuth && !userStore.isLoggedIn && !schoolMode) {
    next('/login')
  } else {
    next()
  }
})

export default router
