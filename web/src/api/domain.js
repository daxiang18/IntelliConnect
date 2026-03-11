import request from '@/utils/request'

export const getDomainConfig = () =>
  request({
    url: '/api/v2/domain-config',
    method: 'get',
    headers: {
      Authorization: `Bearer ${localStorage.getItem('access-token')}`,
    },
  })
