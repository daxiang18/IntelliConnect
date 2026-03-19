// Shared 域路由 — 平台配置（LLM / MCP / Skills / 角色 / 路由 / 记忆体等）
// 任何域模式下均可见（domain: 'shared'）
export default [
  {
    path: '/sharedConfig',
    name: 'SharedConfig',
    component: () => import('@/layout/defaultRouter.vue'),
    meta: {
      title: 'sharedConfigPage',
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      icon: 'RobotOutlined',
      domain: 'shared',
    },
    redirect: { path: '/productRole' },
    children: [
      {
        path: '/productRole',
        name: 'productRole',
        component: () => import('@/views/productRole/index.vue'),
        meta: {
          title: 'productRole',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/productRouterSet',
        name: 'productRouterSet',
        component: () => import('@/views/productRouterSet/index.vue'),
        meta: {
          title: 'productRouterSet',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/productKnowledge',
        name: 'productKnowledge',
        component: () => import('@/views/productKnowledge/index.vue'),
        meta: {
          title: 'productKnowledge',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/productMcp',
        name: 'productMcp',
        component: () => import('@/views/productMcp/index.vue'),
        meta: {
          title: 'productMcp',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/productSkills',
        name: 'productSkills',
        component: () => import('@/views/productSkills/index.vue'),
        meta: {
          title: 'productSkills',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/llmProviderInformation',
        name: 'llmProviderInformation',
        component: () => import('@/views/llmProviderInformation/index.vue'),
        meta: {
          title: 'llmProviderInformation',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/productLlmModel',
        name: 'productLlmModel',
        component: () => import('@/views/productLlmModel/index.vue'),
        meta: {
          title: 'productLlmModel',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/agentMemory',
        name: 'agentMemory',
        component: () => import('@/views/productAgentMemory/index.vue'),
        meta: {
          title: 'agentMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/agentLongMemory',
        name: 'agentLongMemory',
        component: () => import('@/views/agentLongMemory/index.vue'),
        meta: {
          title: 'agentLongMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
      {
        path: '/productXiaoZhi',
        name: 'productXiaoZhi',
        component: () => import('@/views/productXiaoZhi/index.vue'),
        meta: {
          title: 'productXiaoZhi',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
          domain: 'shared',
        },
      },
    ],
  },
]
