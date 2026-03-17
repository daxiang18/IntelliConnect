import request from '@/utils/request'

export const loginIn = (data) =>
  request({
    url: '/api/v2/login',
    method: 'post',
    data,
  })

export const register = (data) =>
  request({
    url: '/api/v2/newUser',
    method: 'post',
    data,
  })
export const forgetPassword = (data) =>
  request({
    url: '/api/v2/forgotPassword',
    method: 'post',
    data,
  })

export const getEmailCode = (data) =>
  request({
    url: '/api/v2/getUserCode',
    method: 'post',
    data,
  })

// 微信扫码登录 - 获取二维码
export const wxScanLoginQrcode = () =>
  request({
    url: '/wxScanLogin/qrcode',
    method: 'get',
  })

// 微信扫码登录 - 检查扫码状态
export const wxScanLoginCheck = (sceneId) =>
  request({
    url: '/wxScanLogin/check',
    method: 'get',
    params: { sceneId },
  })
