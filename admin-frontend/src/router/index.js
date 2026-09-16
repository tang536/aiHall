import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '管理员登录' }
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminRoute.vue'),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true, role: 'ADMIN' },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '数据概览' }
      },
      {
        path: 'repairs',
        name: 'RepairManage',
        component: () => import('@/views/admin/RepairManage.vue'),
        meta: { title: '报修管理' }
      },
      {
        path: 'applications',
        name: 'ApplicationManage',
        component: () => import('@/views/admin/ApplicationManage.vue'),
        meta: { title: '事项申请管理' }
      },
      {
        path: 'notifications',
        name: 'NotificationManage',
        component: () => import('@/views/admin/NotificationManage.vue'),
        meta: { title: '通知公告管理' }
      },
      {
        path: 'knowledge',
        name: 'KnowledgeManage',
        component: () => import('@/views/admin/KnowledgeManage.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: 'locations',
        name: 'LocationManage',
        component: () => import('@/views/admin/LocationManage.vue'),
        meta: { title: '地点管理' }
      },
      {
        path: 'settings',
        name: 'SystemSettings',
        component: () => import('@/views/admin/SystemSettings.vue'),
        meta: { title: '系统设置' }
      },
      {
        path: 'feedbacks',
        name: 'FeedbackManage',
        component: () => import('@/views/admin/FeedbackManage.vue'),
        meta: { title: '学生反馈' }
      },
      {
        path: 'market',
        name: 'MarketManage',
        component: () => import('@/views/admin/MarketManage.vue'),
        meta: { title: '二手商品管理' }
      },
      {
        path: 'posts',
        name: 'PostManage',
        component: () => import('@/views/admin/PostManage.vue'),
        meta: { title: '帖子管理' }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/admin/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  document.title = to.meta.title ? `${to.meta.title} - 管理员后台` : '管理员后台'

  if (to.path === '/login') {
    if (userStore.isLoggedIn && userStore.role === 'ADMIN') {
      next('/admin/dashboard')
    } else {
      next()
    }
    return
  }

  if (to.meta.requiresAuth) {
    if (!userStore.isLoggedIn) {
      next('/login')
    } else if (to.meta.role && userStore.role !== to.meta.role) {
      next('/login')
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
