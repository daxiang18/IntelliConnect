export default [
  {
    path: '/product',
    name: 'Product',
    component: () => import('@/layout/defaultRouter.vue'),
    meta: {
      title: 'productPage',
      auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
      icon: 'KeyOutlined',
    },
    redirect: {
      path: '/product',
    },
    children: [
      {
        path: '/product',
        name: 'subProduct',
        component: () => import('@/views/product/index.vue'),
        meta: {
          title: 'productAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productXiaoZhi',
        name: 'productXiaoZhi',
        component: () => import('@/views/productXiaoZhi/index.vue'),
        meta: {
          title: 'productXiaoZhi',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productRole',
        name: 'productRole',
        component: () => import('@/views/productRole/index.vue'),
        meta: {
          title: 'productRole',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      }, //agentLongMemory
      {
        path: '/agentLongMemory',
        name: 'agentLongMemory',
        component: () => import('@/views/agentLongMemory/index.vue'),
        meta: {
          title: 'agentLongMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/agentMemory',
        name: 'agentMemory',
        component: () => import('@/views/productAgentMemory/index.vue'),
        meta: {
          title: 'agentMemory',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productRouterSet',
        name: 'productRouterSet',
        component: () => import('@/views/productRouterSet/index.vue'),
        meta: {
          title: 'productRouterSet',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productKnowledge',
        name: 'productKnowledge',
        component: () => import('@/views/productKnowledge/index.vue'),
        meta: {
          title: 'productKnowledge',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productMcp',
        name: 'productMcp',
        component: () => import('@/views/productMcp/index.vue'),
        meta: {
          title: 'productMcp',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productSkills',
        name: 'productSkills',
        component: () => import('@/views/productSkills/index.vue'),
        meta: {
          title: 'productSkills',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productModel',
        name: 'productModel',
        component: () => import('@/views/productModel/index.vue'),
        meta: {
          title: 'productModelAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productDataAdd',
        name: 'productDataAdd',
        component: () => import('@/views/productData/index.vue'),
        meta: {
          title: 'productDataAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productFunctionAdd',
        name: 'productFunctionAdd',
        component: () => import('@/views/productFunction/index.vue'),
        meta: {
          title: 'productFunctionAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productDeviceAdd',
        name: 'productDeviceAdd',
        component: () => import('@/views/productDevice/index.vue'),
        meta: {
          title: 'productDeviceAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productEventAdd',
        name: 'productEventAdd',
        component: () => import('@/views/productEvent/index.vue'),
        meta: {
          title: 'productEventAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productEventDataAdd',
        name: 'productEventDataAdd',
        component: () => import('@/views/productEventData/index.vue'),
        meta: {
          title: 'productEventDataAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productOta',
        name: 'productOta',
        component: () => import('@/views/productOta/index.vue'),
        meta: {
          title: 'productOta',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productOtaPassiveXiaoZhi',
        name: 'productOtaPassiveXiaoZhi',
        component: () => import('@/views/productOtaPassiveXiaoZhi/index.vue'),
        meta: {
          title: 'productOtaPassiveXiaoZhi',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productOtaPassive',
        name: 'productOtaPassive',
        component: () => import('@/views/productOtaPassive/index.vue'),
        meta: {
          title: 'productOtaPassive',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/alarmEventAdd',
        name: 'alarmEventAdd',
        component: () => import('@/views/alarmEvent/index.vue'),
        meta: {
          title: 'alarmEventAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/llmProviderInformation',
        name: 'llmProviderInformation',
        component: () => import('@/views/llmProviderInformation/index.vue'),
        meta: {
          title: 'llmProviderInformation',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/productLlmModel',
        name: 'productLlmModel',
        component: () => import('@/views/productLlmModel/index.vue'),
        meta: {
          title: 'productLlmModel',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
      {
        path: '/knowledgeGraphic',
        name: 'knowledgeGraphic',
        component: () => import('@/views/knowledgeGraphic/index.vue'),
        meta: {
          title: 'knowledgeGraphic',
          auth: ['[ROLE_admin]', '[ROLE_guest]', '[ROLE_wx_user]'],
        },
      },
    ],
  },
]
