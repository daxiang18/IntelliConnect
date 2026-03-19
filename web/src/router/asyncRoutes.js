import hub from './modules/hub'
import iot from './modules/iot'
import shared from './modules/shared'
import nestMenu from './modules/nestMenu'
import permission from './modules/permission'

export const asyncRoutes = [
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: {
      title: 'homePage',
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      icon: 'HomeOutlined',
      domain: 'shared',
    },
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: {
      title: 'dashboard',
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      icon: 'DashboardOutlined',
      domain: 'shared',
    },
  },
  {
    path: '/setting',
    name: 'Setting',
    component: () => import('@/views/setting/index.vue'),
    meta: {
      title: 'setting',
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      icon: 'SettingOutlined',
      isHidden: true,
      domain: 'shared',
    },
  },
  //...nestMenu,
  ...hub,
  ...iot,
  ...shared,
  ...permission,
  {
    path: '/about',
    name: 'About',
    component: () => import('@/views/about/index.vue'),
    meta: {
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      title: 'about',
      domain: 'shared',
    },
  },
]
