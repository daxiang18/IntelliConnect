import request from '@/utils/request'
import store from '@/store'

const getToken = () => store.getters['auth/token']

/** 查询收件箱消息列表 */
export const getInboxMessages = (params) =>
  request({
    url: '/api/v2/input/messages',
    method: 'get',
    headers: { Authorization: getToken() },
    params,
  })

/** 创建消息（速记/手动输入） */
export const createMessage = (data) =>
  request({
    url: '/api/v2/input/messages',
    method: 'post',
    headers: { Authorization: getToken() },
    data,
  })

/** 触发消息处理 */
export const processMessage = (id) =>
  request({
    url: `/api/v2/input/messages/${id}/process`,
    method: 'post',
    headers: { Authorization: getToken() },
  })

/** 重试失败消息 */
export const retryMessage = (id) =>
  request({
    url: `/api/v2/input/messages/${id}/retry`,
    method: 'post',
    headers: { Authorization: getToken() },
  })

/** 查询归档消息列表（支持关键词搜索） */
export const getArchivedMessages = (params) =>
  request({
    url: '/api/v2/input/messages',
    method: 'get',
    headers: { Authorization: getToken() },
    params,
  })

/** 获取消息统计数据 */
export const getMessageStats = () =>
  request({
    url: '/api/v2/input/messages/stats',
    method: 'get',
    headers: { Authorization: getToken() },
  })

/** 查询单条消息详情 */
export const getMessageById = (id) =>
  request({
    url: `/api/v2/input/messages/${id}`,
    method: 'get',
    headers: { Authorization: getToken() },
  })

/** 删除消息 */
export const deleteMessage = (id) =>
  request({
    url: `/api/v2/input/messages/${id}`,
    method: 'delete',
    headers: { Authorization: getToken() },
  })

/** 批量处理消息 */
export const batchProcessMessages = (ids) =>
  request({
    url: '/api/v2/input/messages/batch-process',
    method: 'post',
    headers: { Authorization: getToken() },
    data: ids,
  })

/** 批量删除消息 */
export const batchDeleteMessages = (ids) =>
  request({
    url: '/api/v2/input/messages/batch-delete',
    method: 'post',
    headers: { Authorization: getToken() },
    data: ids,
  })

/** 更新消息文档用途 */
export const updateMessagePurpose = (id, documentPurpose) =>
  request({
    url: `/api/v2/input/messages/${id}/purpose`,
    method: 'put',
    headers: { Authorization: getToken() },
    params: { documentPurpose },
  })

/** 获取同步统计数据 */
export const getSyncStats = () =>
  request({
    url: '/api/v2/input/messages/sync-stats',
    method: 'get',
    headers: { Authorization: getToken() },
  })

/** 获取同步消息列表 */
export const getSyncMessages = (params) =>
  request({
    url: '/api/v2/input/messages/sync-list',
    method: 'get',
    headers: { Authorization: getToken() },
    params,
  })
