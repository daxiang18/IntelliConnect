import { createStore } from 'vuex'

import app from './app'
import auth from './auth'
import domain from './domain'

const store = createStore({
  strict: true,
  modules: {
    app,
    auth,
    domain,
  },
})
export default store
