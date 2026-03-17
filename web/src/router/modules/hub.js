// Hub 域路由 — 个人中枢 + AI / 智能体 / 知识库相关页面
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
  {
    path: '/hubConfig',
    name: 'HubConfig',
    component: () => import('@/layout/defaultRouter.vue'),
    meta: {
      title: 'hubPage',
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      icon: 'RobotOutlined',
      domain: 'hub',
    },
    redirect: { path: '/productRole' },
    children: [
      {
        path: '/productXiaoZhi',
        name: 'productXiaoZhi',
        component: () => import('@/views/productXiaoZhi/index.vue'),
        meta: {
          title: 'productXiaoZhi',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/productRole',
        name: 'productRole',
        component: () => import('@/views/productRole/index.vue'),
        meta: {
          title: 'productRole',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/agentLongMemory',
        name: 'agentLongMemory',
        component: () => import('@/views/agentLongMemory/index.vue'),
        meta: {
          title: 'agentLongMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/agentMemory',
        name: 'agentMemory',
        component: () => import('@/views/productAgentMemory/index.vue'),
        meta: {
          title: 'agentMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/productRouterSet',
        name: 'productRouterSet',
        component: () => import('@/views/productRouterSet/index.vue'),
        meta: {
          title: 'productRouterSet',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/productKnowledge',
        name: 'productKnowledge',
        component: () => import('@/views/productKnowledge/index.vue'),
        meta: {
          title: 'productKnowledge',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/productMcp',
        name: 'productMcp',
        component: () => import('@/views/productMcp/index.vue'),
        meta: {
          title: 'productMcp',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/productSkills',
        name: 'productSkills',
        component: () => import('@/views/productSkills/index.vue'),
        meta: {
          title: 'productSkills',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/llmProviderInformation',
        name: 'llmProviderInformation',
        component: () => import('@/views/llmProviderInformation/index.vue'),
        meta: {
          title: 'llmProviderInformation',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'hub',
        },
      },
      {
        path: '/productLlmModel',
        name: 'productLlmModel',
        component: () => import('@/views/productLlmModel/index.vue'),
        meta: {
          title: 'productLlmModel',
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
    ],
  },
]
