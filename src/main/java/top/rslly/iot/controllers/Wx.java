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
package top.rslly.iot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.DigestUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.rslly.iot.param.request.WxBindProduct;
import top.rslly.iot.param.request.WxUser;
import top.rslly.iot.services.wechat.WxProductBindServiceImpl;
import top.rslly.iot.services.wechat.WxUserServiceImpl;
import top.rslly.iot.utility.SHA1;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.wx.DealMsg;
import top.rslly.iot.utility.wx.DealWx;
import top.rslly.iot.utility.wx.SmartRobot;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RestController
@Slf4j
@Validated
/**
 * 仅保留当前官方微信能力：
 * 1. 微信登录
 * 2. 微信与产品绑定
 * 3. 官方微信消息回调
 *
 * 未来网页微信/QQ/飞书等侧车输入统一走 InputController，
 * 不在这里扩展新的侧车协议适配逻辑。
 */
public class Wx {

  @Autowired
  private WxUserServiceImpl wxUserService;
  @Autowired
  private WxProductBindServiceImpl wxProductBindService;
  @Autowired
  private DealMsg dealMsg;

  @RequestMapping(value = "/wxLogin", method = RequestMethod.POST)
  public JsonResult<?> wxLogin(@Valid @RequestBody WxUser wxUser) throws IOException {
    return wxUserService.wxLogin(wxUser);
  }

  @RequestMapping(value = "/wxBindProduct", method = RequestMethod.GET)
  public JsonResult<?> wxBindProduct(@RequestHeader("Authorization") String header) {
    return wxProductBindService.wxGetBindProduct(header);
  }

  @RequestMapping(value = "/wxBindProduct", method = RequestMethod.POST)
  public JsonResult<?> wxBindProduct(@Valid @RequestBody WxBindProduct wxBindProduct,
      @RequestHeader("Authorization") String header) {
    return wxProductBindService.wxBindProduct(wxBindProduct, header);
  }

  @RequestMapping(value = "/wxBindProduct", method = RequestMethod.DELETE)
  public JsonResult<?> wxUnBindProduct(@Valid @RequestBody WxBindProduct wxBindProduct,
      @RequestHeader("Authorization") String header) {
    return wxProductBindService.wxUnBindProduct(wxBindProduct, header);
  }

  /**
   * 官方微信回调入口。仅处理当前已支持的公众号/小程序消息回调，
   * 不作为未来侧车消息的统一接入点。
   */
  @RequestMapping(value = "/wxmsg", method = {RequestMethod.GET, RequestMethod.POST})
  public void WxMsg(HttpServletRequest request, HttpServletResponse response) {
    dealMsg.WxMsg(request, response);
  }

  // ==================== 微信公众号扫码登录 ====================

  /**
   * 生成扫码登录二维码
   * <p>
   * 前端调用此接口获取带参二维码 URL 和 sceneId，然后展示二维码给用户扫描，
   * 同时使用 sceneId 轮询 /wxScanLogin/check 接口检查登录状态。
   */
  @RequestMapping(value = "/wxScanLogin/qrcode", method = RequestMethod.GET)
  public JsonResult<?> wxScanLoginQrCode() throws IOException {
    return wxUserService.generateScanLoginQrCode();
  }

  /**
   * 检查扫码登录状态（前端轮询）
   * <p>
   * 返回状态：waiting-等待扫码, scanned-已扫码（同时返回 token）, expired-已过期
   *
   * @param sceneId 二维码场景 ID
   */
  @RequestMapping(value = "/wxScanLogin/check", method = RequestMethod.GET)
  public JsonResult<?> wxScanLoginCheck(@RequestParam("sceneId") String sceneId) {
    return wxUserService.checkScanLoginStatus(sceneId);
  }
}
