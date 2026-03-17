import router, { constantRoutes, asyncRoutes } from '@/router'
import { jwtDecode } from 'jwt-decode'
import { loginIn } from '@/api/user'
import { start } from 'nprogress'

const state = {
  auth: localStorage.getItem('access-token') || '',
  menuList: [],
}
const getters = {
  token: () => {
    return state.auth
  },
}
const mutations = {
  async GENERATE_ROUTES(state, { auth, domainState }) {
    const layout = constantRoutes.find((item) => item.path === '/')
    let authRoutes = traversalRoutes(asyncRoutes, auth)

    // Filter by domain if domainState is provided
    if (domainState) {
      authRoutes = filterByDomain(authRoutes, domainState)
    }

    layout.children = [...authRoutes]
    state.menuList = authRoutes
    state.auth = auth
    constantRoutes.forEach((r) => router.addRoute(r))
  },
  SET_AUTH(state, auth) {
    state.auth = auth
    localStorage.setItem('access-token', auth)
  },
  CLEAR_AUTH(state) {
    localStorage.removeItem('access-token')
    state.auth = ''
  },
}
const actions = {}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions,
}

function traversalRoutes(routes, auth) {
  const result = []
  const decoded = jwtDecode(auth)
  // JWT role 可能是 "ROLE_xxx"（微信登录）或 "[ROLE_xxx]"（账号密码登录）
  // 路由 meta.auth 数组格式统一为 "[ROLE_xxx]"
  // 需要同时匹配两种格式
  const rawRole = decoded.role || ''
  const normalizedRole = rawRole.startsWith('[') ? rawRole : '[' + rawRole + ']'
  routes.forEach((r) => {
    let { meta, children } = r
    if (meta.auth.includes(rawRole) || meta.auth.includes(normalizedRole)) {
      if (children && children.length) {
        r.children = traversalRoutes(children, auth)
      }
      result.push(r)
    }
  })
  return result
}

function filterByDomain(routes, domainState) {
  return routes.filter((r) => {
    const domain = r.meta && r.meta.domain
    if (!domain || domain === 'shared') return true
    if (domain === 'hub') return domainState.hub && domainState.hub.enabled
    if (domain === 'iot') return domainState.iot && domainState.iot.enabled
    return true
  })
}
