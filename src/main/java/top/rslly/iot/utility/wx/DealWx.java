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
package top.rslly.iot.utility.wx;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.rslly.iot.utility.HttpRequestUtils;
import top.rslly.iot.utility.RedisUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class DealWx {
  @Autowired
  private HttpRequestUtils httpRequestUtils;
  @Autowired
  private RedisUtil redisUtil;
  @Value("${wx.appid}")
  private String appid;
  @Value("${wx.templateUrl}")
  private String projectUrl;
  @Value("${wx.base-url}")
  private String baseUrl;

  public String getAccessToken(String appid, String appSecret) throws IOException {
    String url = baseUrl + "/cgi-bin/token?grant_type=client_credential&appid="
        + appid + "&secret=" + appSecret;
    Response s = httpRequestUtils.httpGet(url);
    return Objects.requireNonNull(s.body()).string();
  }

  public String getOpenid(String code, String microappid, String microappsecret)
      throws IOException {
    String url = baseUrl + "/sns/jscode2session?appid=" + microappid + "&secret="
        + microappsecret + "&js_code=" + code + "&grant_type=authorization_code";
    return Objects.requireNonNull(httpRequestUtils.httpGet(url).body()).string();
  }

  public void templatePost(JSONObject reqdata, String templateid, String openid) {
    String accessToken = (String) redisUtil.get(appid);
    String url =
        baseUrl + "/cgi-bin/message/template/send?access_token=" + accessToken;
    // String reqBody = "{\"touser\":\"" + openid + "\", \"template_id\":\"" + templateid + "\",
    // \"url\":\"" + fxurl + "\", \"data\": " + reqdata + "}";
    // 优化reqbody, 不用拼接
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("touser", openid);
    jsonObject.put("template_id", templateid);
    jsonObject.put("url", projectUrl);
    jsonObject.put("data", reqdata);
    try {
      // log.info("发送模板消息{}", jsonObject.toJSONString());
      httpRequestUtils.asyncPostByJson(url, jsonObject.toJSONString());
    } catch (IOException e) {
      log.error("发送模板消息失败{}", e.getMessage());
    }
  }

  public void sendContent(String openid, String content, String microappid) throws IOException {
    String token = (String) redisUtil.get(microappid);
    String url = baseUrl + "/cgi-bin/message/custom/send?access_token=" + token;
    // String jsonstr = "{\n" + " \"touser\":\"" + openid + "\",\n" + " \"msgtype\":\"text\",\n"
    // + " \"text\":\n" + " {\n" + " \"content\":\"" + content + "\"\n" + " }\n" + "}";
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("touser", openid);
    jsonObject.put("msgtype", "text");
    Map<String, String> textMap = new HashMap<>();
    textMap.put("content", content);
    jsonObject.put("text", textMap);
    httpRequestUtils.asyncPostByJson(url, jsonObject.toJSONString());
  }

  public String getMedia(String mediaId, String microappid) throws IOException {
    log.info(mediaId);
    String token = (String) redisUtil.get(microappid);
    String url = baseUrl + "/cgi-bin/media/get?access_token=" + token + "&media_id="
        + mediaId;
    log.info(url);
    return url;
    // Response response=httpRequestUtils.httpGet(url);
    // System.out.println(response.body());
  }

  /**
   * 获取公众号 access_token（带 Redis 缓存，有效期 7000 秒）
   */
  public String getCachedAccessToken(String appid, String appSecret) throws IOException {
    String cacheKey = "wx_access_token:" + appid;
    String token = (String) redisUtil.get(cacheKey);
    if (token != null && !token.isEmpty()) {
      return token;
    }
    String result = getAccessToken(appid, appSecret);
    JSONObject json = JSONObject.parseObject(result);
    token = json.getString("access_token");
    if (token != null && !token.isEmpty()) {
      // 微信 access_token 有效期 7200 秒，缓存 7000 秒留一些余量
      redisUtil.set(cacheKey, token, 7000);
    } else {
      log.error("获取公众号 access_token 失败: {}", result);
    }
    return token;
  }

  /**
   * 创建带参数的临时二维码（用于扫码登录）
   *
   * @param accessToken 公众号 access_token
   * @param sceneStr 场景值字符串（如 UUID）
   * @param expireSeconds 二维码过期秒数（最大 2592000 即 30 天）
   * @return 包含 ticket、expire_seconds、url 的 JSON 字符串
   */
  public String createTempQrCode(String accessToken, String sceneStr, int expireSeconds)
      throws IOException {
    String url = baseUrl + "/cgi-bin/qrcode/create?access_token=" + accessToken;
    JSONObject body = new JSONObject();
    body.put("expire_seconds", expireSeconds);
    body.put("action_name", "QR_STR_SCENE");
    JSONObject actionInfo = new JSONObject();
    JSONObject scene = new JSONObject();
    scene.put("scene_str", sceneStr);
    actionInfo.put("scene", scene);
    body.put("action_info", actionInfo);
    String result = httpRequestUtils.createHttpsPostByjson(url, body.toJSONString());
    log.info("创建临时二维码结果: {}", result);
    return result;
  }

  /**
   * 通过 ticket 换取二维码图片 URL
   *
   * @param ticket 二维码 ticket（需要 URL encode）
   * @return 二维码图片 URL
   */
  public String getQrCodeUrl(String ticket) {
    try {
      String encodedTicket = java.net.URLEncoder.encode(ticket, "UTF-8");
      return "https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=" + encodedTicket;
    } catch (Exception e) {
      log.error("编码 ticket 失败", e);
      return null;
    }
  }

}
