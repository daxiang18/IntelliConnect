import request from '@/utils/request'
import store from '@/store'

const token = store.getters['auth/token']

export const getHubPersona = (params) =>
  request({
    url: '/api/v2/hub/persona',
    method: 'get',
    params,
    headers: { Authorization: token },
  })

export const createHubPersona = (data) =>
  request({
    url: '/api/v2/hub/persona',
    method: 'post',
    data,
    headers: { Authorization: token },
  })

export const updateHubPersona = (data) =>
  request({
    url: '/api/v2/hub/persona',
    method: 'put',
    data,
    headers: { Authorization: token },
  })

export const deleteHubPersona = (params) =>
  request({
    url: '/api/v2/hub/persona',
    method: 'delete',
    params,
    headers: { Authorization: token },
  })
