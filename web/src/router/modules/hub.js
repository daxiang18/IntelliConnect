// Hub 域路由 — 个人中枢核心页面
export default [
  {
    path: '/hubCore',
    name: 'HubCore',
    component: () => import('@/layout/defaultRouter.vue'),
    meta: {
      title: 'hubCorePage',
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      icon: 'InboxOutlined',
      domain: 'hub',
    },
    redirect: { path: '/hubDashboard' },
    children: [
      {
        path: '/hubDashboard',
        name: 'hubDashboard',
        component: () => import('@/views/hubDashboard/index.vue'),
        meta: {
          title: 'hubDashboard',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/inbox',
        name: 'inbox',
        component: () => import('@/views/inbox/index.vue'),
        meta: {
          title: 'inbox',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/quickNote',
        name: 'quickNote',
        component: () => import('@/views/quickNote/index.vue'),
        meta: {
          title: 'quickNote',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/archive',
        name: 'archive',
        component: () => import('@/views/archive/index.vue'),
        meta: {
          title: 'archive',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/syncStatus',
        name: 'syncStatus',
        component: () => import('@/views/syncStatus/index.vue'),
        meta: {
          title: 'syncStatus',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/todoList',
        name: 'todoList',
        component: () => import('@/views/todoList/index.vue'),
        meta: {
          title: 'todoList',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/hubPersona',
        name: 'hubPersona',
        component: () => import('@/views/hubPersona/index.vue'),
        meta: {
          title: 'hubPersona',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/hubPipeline',
        name: 'hubPipeline',
        component: () => import('@/views/hubPipeline/index.vue'),
        meta: {
          title: 'hubPipeline',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/knowledgeGraphic',
        name: 'knowledgeGraphic',
        component: () => import('@/views/knowledgeGraphic/index.vue'),
        meta: {
          title: 'knowledgeGraphic',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/messageDetail/:id',
        name: 'messageDetail',
        component: () => import('@/views/messageDetail/index.vue'),
        meta: {
          title: 'messageDetail',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
          hidden: true, // 不在侧边栏显示
        },
      },
    ],
  },
]
