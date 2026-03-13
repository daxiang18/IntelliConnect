import { getDomainConfig } from '@/api/domain'

const state = {
  loaded: false,
  hub: { enabled: true, defaultEntry: '/home', menuGroup: 'hub' },
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
  SET_DOMAIN_LOADED(state) {
    state.loaded = true
  },
}

const actions = {
  async fetchDomainConfig({ commit }) {
    try {
      const res = await getDomainConfig()
      if (res.data && res.data.success && res.data.data) {
        commit('SET_DOMAIN_CONFIG', res.data.data)
        return
      }
    } catch (e) {
      console.warn('[domain] Failed to fetch domain config, using defaults:', e.message)
    }

    commit('SET_DOMAIN_LOADED')
  },
}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions,
}
