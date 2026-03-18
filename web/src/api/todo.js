import request from '@/utils/request'
import store from '@/store'

const getToken = () => store.getters['auth/token']

/** 查询待办列表（分页） */
export const getTodos = (params) =>
  request({
    url: '/api/v2/todos',
    method: 'get',
    headers: { Authorization: getToken() },
    params,
  })

/** 查询某消息关联的待办 */
export const getTodosByMessage = (messageId) =>
  request({
    url: `/api/v2/todos/by-message/${messageId}`,
    method: 'get',
    headers: { Authorization: getToken() },
  })

/** 更新待办状态 */
export const updateTodoStatus = (id, status) =>
  request({
    url: `/api/v2/todos/${id}/status`,
    method: 'put',
    headers: { Authorization: getToken() },
    params: { status },
  })

/** 删除待办 */
export const deleteTodo = (id) =>
  request({
    url: `/api/v2/todos/${id}`,
    method: 'delete',
    headers: { Authorization: getToken() },
  })
