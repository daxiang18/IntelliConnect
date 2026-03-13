import 'virtual:svg-icons-register' // 注册雪碧图插件

import { createApp } from 'vue'
import App from './App.vue'
import router from '@/router/index.js'
import store from '@/store'
import { setupI18n } from '@/i18n'
import { setupAntd } from '@/plugins/antd'
import { setupComponent } from '@/components'
import { setupDirective } from '@/directives'
import { resolveTargetPath } from '@/utils/domain'

import nProgress from '@/plugins/nProgress'
// 全局样式
import '@/assets/global.scss'
import '@/assets/common.scss'

const PUBLIC_PATHS = new Set(['/login', '/register', '/forgotPassword'])

async function ensureDomainConfig() {
  if (!store.state.domain.loaded) {
    await store.dispatch('domain/fetchDomainConfig')
  }
  return store.state.domain
}

router.beforeEach(async (to, from, next) => {
  nProgress.start()
  const token = store.getters['auth/token']
  if (PUBLIC_PATHS.has(to.path)) {
    if (token) {
      const domainState = await ensureDomainConfig()
      return next({
        path: resolveTargetPath(domainState, to.query.redirect),
        replace: true,
      })
    }
    next()
  } else {
    if (token) {
      const domainState = await ensureDomainConfig()
      const menuList = store.state.auth.menuList
      if (!menuList.length) {
        const auth = token
        store.commit('auth/GENERATE_ROUTES', { auth, domainState })
        return next({
          path: resolveTargetPath(domainState, to.path),
          replace: true,
        })
      }

      if (to.path === '/' || !to.matched.length) {
        return next({
          path: resolveTargetPath(domainState),
          replace: true,
        })
      }

      next()
    } else {
      next({
        path: '/login',
        query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : undefined,
      })
    }
  }
})
router.afterEach((to, from) => {
  nProgress.done()
})
const app = createApp(App)
// 注册 ant-design-vue 组件和图标
setupAntd(app)
// 注册全局组件
setupComponent(app)
// 注册自定义指令
setupDirective(app)

async function initApp(app) {
  await setupI18n(app)
  app.use(store).use(router).mount('#app')
}
initApp(app)
