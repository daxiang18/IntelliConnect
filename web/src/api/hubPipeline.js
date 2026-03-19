import request from '@/utils/request'
import store from '@/store'

const token = store.getters['auth/token']

export const getHubPipeline = (params) =>
  request({
    url: '/api/v2/hub/pipeline',
    method: 'get',
    params,
    headers: { Authorization: token },
  })

export const createHubPipeline = (data) =>
  request({
    url: '/api/v2/hub/pipeline',
    method: 'post',
    data,
    headers: { Authorization: token },
  })

export const updateHubPipeline = (data) =>
  request({
    url: '/api/v2/hub/pipeline',
    method: 'put',
    data,
    headers: { Authorization: token },
  })
