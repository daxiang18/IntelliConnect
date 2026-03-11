import { getDomainConfig } from '@/api/domain'

const state = {
  loaded: false,
  hub: { enabled: true, defaultEntry: '/dashboard', menuGroup: 'hub' },
  iot: { enabled: true, defaultEntry: '/product', menuGroup: 'iot' },
}

const getters = {
  hubEnabled: (state) => state.hub.enabled,
  iotEnabled: (state) => state.iot.enabled,
  domainLoaded: (state) => state.loaded,
}

const mutations = {
  SET_DOMAIN_CONFIG(state, config) {
    if (config.hub) state.hub = { ...state.hub, ...config.hub }
    if (config.iot) state.iot = { ...state.iot, ...config.iot }
    state.loaded = true
  },
}

const actions = {
  async fetchDomainConfig({ commit }) {
    try {
      const res = await getDomainConfig()
      if (res.data && res.data.success && res.data.data) {
        commit('SET_DOMAIN_CONFIG', res.data.data)
      }
    } catch (e) {
      console.warn('[domain] Failed to fetch domain config, using defaults:', e.message)
    }
  },
}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions,
}
