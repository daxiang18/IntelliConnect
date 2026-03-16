/**
 * Copyright © 2023-2030 The ruanrongman Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.rslly.iot.services.wechat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.rslly.iot.dao.WxUserRepository;
import top.rslly.iot.models.WxUserEntity;
import top.rslly.iot.param.request.WxUser;
import top.rslly.iot.param.response.WxLoginResponse;
import top.rslly.iot.param.response.WxScanCheckResponse;
import top.rslly.iot.param.response.WxScanLoginResponse;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.RedisUtil;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;
import top.rslly.iot.utility.wx.DealWx;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class WxUserServiceImpl implements WxUserService {
  @Resource
  private WxUserRepository wxUserRepository;
  @Autowired
  private DealWx dealWx;
  @Autowired
  private RedisUtil redisUtil;
  @Value("${wx.micro.appid}")
  private String microAppid;
  @Value("${wx.micro.appsecret}")
  private String microAppSecret;
  @Value("${wx.appid}")
  private String officialAppid;
  @Value("${wx.appsecret}")
  private String officialAppSecret;
  @Value("${wx.scan-login.expire-seconds:300}")
  private int scanLoginExpireSeconds;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> wxLogin(WxUser wxUser) throws IOException {
    String s = dealWx.getOpenid(wxUser.getCode(), microAppid, microAppSecret);
    log.info(s);
    String openid = (String) JSON.parseObject(s).get("openid");
    if (openid == null) {
      return ResultTool.fail(ResultCode.USER_CODE_ERROR);
    }
    // System.out.println(openid);
    List<WxUserEntity> user = wxUserRepository.findAllByAppidAndOpenid(microAppid, openid);
    WxLoginResponse wxLoginResponse = new WxLoginResponse();
    WxUserEntity currentUser;
    if (user.isEmpty()) {
      // 新用户注册
      WxUserEntity wxUserEntity = new WxUserEntity();
      wxUserEntity.setName(UUID.randomUUID().toString());
      wxUserEntity.setOpenid(openid);
      wxUserEntity.setAppid(microAppid);
      wxUserRepository.save(wxUserEntity);
      currentUser = wxUserEntity; // 使用刚创建的用户对象
      wxLoginResponse.setIsNewUser(true);
    } else {
      // 老用户
      currentUser = user.get(0); // 获取已存在的用户
      wxLoginResponse.setIsNewUser(false);
    }
    wxLoginResponse.setToken(JwtTokenUtil.TOKEN_PREFIX
        + JwtTokenUtil.createToken(currentUser.getName(), "ROLE_" + "wx_user"));
    return ResultTool.success(wxLoginResponse);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public WxUserEntity wxRegister(String appid, String openid) {
    if (appid == null || appid.trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid appid");
    }
    // 校验 openid 是否有效
    if (openid == null || openid.trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid openid");
    }
    if (wxUserRepository.findAllByAppidAndOpenid(appid, openid).isEmpty()) {
      WxUserEntity wxUserEntity = new WxUserEntity();
      wxUserEntity.setName(UUID.randomUUID().toString());
      wxUserEntity.setOpenid(openid);
      wxUserEntity.setAppid(appid);
      wxUserRepository.save(wxUserEntity);
      return wxUserEntity;
    }
    return null;
  }

  @Override
  public List<WxUserEntity> findAllByAppidAndOpenid(String appid, String openid) {
    return wxUserRepository.findAllByAppidAndOpenid(appid, openid);
  }

  @Override
  public List<WxUserEntity> findAllByName(String name) {
    return wxUserRepository.findAllByName(name);
  }

  @Override
  public JsonResult<?> wxGetAllUser() {
    return ResultTool.success(wxUserRepository.findAll());
  }

  // ==================== 扫码登录相关方法 ====================

  private static final String SCAN_LOGIN_PREFIX = "wx_scan_login:";

  @Override
  public JsonResult<?> generateScanLoginQrCode() throws IOException {
    // 1. 生成唯一场景值
    String sceneId = UUID.randomUUID().toString().replace("-", "");

    // 2. 获取公众号 access_token
    String accessToken = dealWx.getCachedAccessToken(officialAppid, officialAppSecret);
    if (accessToken == null || accessToken.isEmpty()) {
      log.error("获取公众号 access_token 失败，无法生成扫码登录二维码");
      return ResultTool.fail(ResultCode.COMMON_FAIL);
    }

    // 3. 调用微信 API 创建带参临时二维码
    String result = dealWx.createTempQrCode(accessToken, sceneId, scanLoginExpireSeconds);
    JSONObject resultJson = JSONObject.parseObject(result);
    String ticket = resultJson.getString("ticket");
    if (ticket == null || ticket.isEmpty()) {
      log.error("创建临时二维码失败: {}", result);
      return ResultTool.fail(ResultCode.COMMON_FAIL);
    }

    // 4. 在 Redis 中标记该 sceneId 为等待扫码状态
    redisUtil.set(SCAN_LOGIN_PREFIX + sceneId, "waiting", scanLoginExpireSeconds);

    // 5. 构建响应
    WxScanLoginResponse response = new WxScanLoginResponse();
    response.setQrCodeUrl(dealWx.getQrCodeUrl(ticket));
    response.setSceneId(sceneId);
    response.setExpireSeconds(scanLoginExpireSeconds);

    log.info("生成扫码登录二维码成功，sceneId={}", sceneId);
    return ResultTool.success(response);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> checkScanLoginStatus(String sceneId) {
    WxScanCheckResponse response = new WxScanCheckResponse();

    Object value = redisUtil.get(SCAN_LOGIN_PREFIX + sceneId);
    if (value == null) {
      // sceneId 已过期或不存在
      response.setStatus("expired");
      response.setIsNewUser(false);
      return ResultTool.success(response);
    }

    String strValue = value.toString();
    if ("waiting".equals(strValue)) {
      // 还没人扫码
      response.setStatus("waiting");
      response.setIsNewUser(false);
      return ResultTool.success(response);
    }

    // 值是 openid，说明用户已扫码
    String openid = strValue;
    response.setStatus("scanned");

    // 查找或创建用户
    List<WxUserEntity> users = wxUserRepository.findAllByAppidAndOpenid(officialAppid, openid);
    WxUserEntity currentUser;
    if (users.isEmpty()) {
      // 新用户自动注册
      WxUserEntity entity = new WxUserEntity();
      entity.setName(UUID.randomUUID().toString());
      entity.setOpenid(openid);
      entity.setAppid(officialAppid);
      wxUserRepository.save(entity);
      currentUser = entity;
      response.setIsNewUser(true);
    } else {
      currentUser = users.get(0);
      response.setIsNewUser(false);
    }

    // 颁发 JWT
    String token = JwtTokenUtil.TOKEN_PREFIX
        + JwtTokenUtil.createToken(currentUser.getName(), "ROLE_wx_user");
    response.setToken(token);

    // 登录完成，清除 Redis 中的场景值（防止重复使用）
    redisUtil.del(SCAN_LOGIN_PREFIX + sceneId);

    log.info("扫码登录成功，sceneId={}, openid={}, isNewUser={}", sceneId, openid, response.getIsNewUser());
    return ResultTool.success(response);
  }

  @Override
  public void handleScanEvent(String openid, String sceneStr) {
    if (sceneStr == null || sceneStr.isEmpty()) {
      return;
    }
    // 检查 Redis 中是否存在该 sceneId 的等待记录
    String redisKey = SCAN_LOGIN_PREFIX + sceneStr;
    Object value = redisUtil.get(redisKey);
    if (value != null && "waiting".equals(value.toString())) {
      // 将值从 "waiting" 更新为用户的 openid，保留原有过期时间
      long ttl = redisUtil.getExpire(redisKey);
      if (ttl > 0) {
        redisUtil.set(redisKey, openid, ttl);
      } else {
        redisUtil.set(redisKey, openid, scanLoginExpireSeconds);
      }
      log.info("扫码事件处理成功，sceneStr={}, openid={}", sceneStr, openid);
    } else {
      log.warn("扫码事件未匹配到等待中的登录请求，sceneStr={}", sceneStr);
    }
  }
}
