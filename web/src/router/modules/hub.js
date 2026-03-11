// Hub 域路由 — AI / 智能体 / 知识库相关页面
export default [
  {
    path: '/hubConfig',
    name: 'HubConfig',
    component: () => import('@/layout/defaultRouter.vue'),
    meta: {
      title: 'hubPage',
      auth: ['[ROLE_admin]', '[ROLE_guest]'],
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
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/productRole',
        name: 'productRole',
        component: () => import('@/views/productRole/index.vue'),
        meta: {
          title: 'productRole',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/agentLongMemory',
        name: 'agentLongMemory',
        component: () => import('@/views/agentLongMemory/index.vue'),
        meta: {
          title: 'agentLongMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/agentMemory',
        name: 'agentMemory',
        component: () => import('@/views/productAgentMemory/index.vue'),
        meta: {
          title: 'agentMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/productRouterSet',
        name: 'productRouterSet',
        component: () => import('@/views/productRouterSet/index.vue'),
        meta: {
          title: 'productRouterSet',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/productKnowledge',
        name: 'productKnowledge',
        component: () => import('@/views/productKnowledge/index.vue'),
        meta: {
          title: 'productKnowledge',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/productMcp',
        name: 'productMcp',
        component: () => import('@/views/productMcp/index.vue'),
        meta: {
          title: 'productMcp',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/productSkills',
        name: 'productSkills',
        component: () => import('@/views/productSkills/index.vue'),
        meta: {
          title: 'productSkills',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/llmProviderInformation',
        name: 'llmProviderInformation',
        component: () => import('@/views/llmProviderInformation/index.vue'),
        meta: {
          title: 'llmProviderInformation',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/productLlmModel',
        name: 'productLlmModel',
        component: () => import('@/views/productLlmModel/index.vue'),
        meta: {
          title: 'productLlmModel',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
      {
        path: '/knowledgeGraphic',
        name: 'knowledgeGraphic',
        component: () => import('@/views/knowledgeGraphic/index.vue'),
        meta: {
          title: 'knowledgeGraphic',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'hub',
        },
      },
    ],
  },
]
