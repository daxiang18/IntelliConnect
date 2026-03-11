// IoT 域路由 — 产品 / 设备 / 物模型 / OTA 相关页面
export default [
  {
    path: '/product',
    name: 'Product',
    component: () => import('@/layout/defaultRouter.vue'),
    meta: {
      title: 'productPage',
      auth: ['[ROLE_admin]', '[ROLE_guest]'],
      icon: 'KeyOutlined',
      domain: 'iot',
    },
    redirect: { path: '/product' },
    children: [
      {
        path: '/product',
        name: 'subProduct',
        component: () => import('@/views/product/index.vue'),
        meta: {
          title: 'productAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productModel',
        name: 'productModel',
        component: () => import('@/views/productModel/index.vue'),
        meta: {
          title: 'productModelAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productDataAdd',
        name: 'productDataAdd',
        component: () => import('@/views/productData/index.vue'),
        meta: {
          title: 'productDataAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productFunctionAdd',
        name: 'productFunctionAdd',
        component: () => import('@/views/productFunction/index.vue'),
        meta: {
          title: 'productFunctionAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productDeviceAdd',
        name: 'productDeviceAdd',
        component: () => import('@/views/productDevice/index.vue'),
        meta: {
          title: 'productDeviceAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productEventAdd',
        name: 'productEventAdd',
        component: () => import('@/views/productEvent/index.vue'),
        meta: {
          title: 'productEventAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productEventDataAdd',
        name: 'productEventDataAdd',
        component: () => import('@/views/productEventData/index.vue'),
        meta: {
          title: 'productEventDataAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productOta',
        name: 'productOta',
        component: () => import('@/views/productOta/index.vue'),
        meta: {
          title: 'productOta',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productOtaPassiveXiaoZhi',
        name: 'productOtaPassiveXiaoZhi',
        component: () => import('@/views/productOtaPassiveXiaoZhi/index.vue'),
        meta: {
          title: 'productOtaPassiveXiaoZhi',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/productOtaPassive',
        name: 'productOtaPassive',
        component: () => import('@/views/productOtaPassive/index.vue'),
        meta: {
          title: 'productOtaPassive',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
      {
        path: '/alarmEventAdd',
        name: 'alarmEventAdd',
        component: () => import('@/views/alarmEvent/index.vue'),
        meta: {
          title: 'alarmEventAdd',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
    ],
  },
  {
    path: '/deviceData',
    name: 'deviceDataPage',
    component: () => import('@/layout/defaultRouter.vue'),
    meta: {
      title: 'deviceDataPage',
      auth: ['[ROLE_admin]', '[ROLE_guest]'],
      icon: 'DashboardOutlined',
      domain: 'iot',
    },
    redirect: { path: '/deviceData' },
    children: [
      {
        path: '/deviceData',
        name: 'deviceData',
        component: () => import('@/views/deviceMoniter/index.vue'),
        meta: {
          title: 'deviceData',
          auth: ['[ROLE_admin]', '[ROLE_guest]'],
          domain: 'iot',
        },
      },
    ],
  },
]
