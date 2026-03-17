import request from '@/utils/request'

/** 查询收件箱消息列表 */
export const getInboxMessages = (params) =>
  request({
    url: '/api/v2/input/messages',
    method: 'get',
    params,
  })

/** 创建消息（速记/手动输入） */
export const createMessage = (data) =>
  request({
    url: '/api/v2/input/messages',
    method: 'post',
    data,
  })

/** 触发消息处理 */
export const processMessage = (id) =>
  request({
    url: `/api/v2/input/messages/${id}/process`,
    method: 'post',
  })

/** 重试失败消息 */
export const retryMessage = (id) =>
  request({
    url: `/api/v2/input/messages/${id}/retry`,
    method: 'post',
  })

/** 查询归档消息列表（支持关键词搜索） */
export const getArchivedMessages = (params) =>
  request({
    url: '/api/v2/input/messages',
    method: 'get',
    params,
  })

/** 获取消息统计数据 */
export const getMessageStats = () =>
  request({
    url: '/api/v2/input/messages/stats',
    method: 'get',
  })
