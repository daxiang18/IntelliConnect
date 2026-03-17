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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * WeChat message forwarder component.
 * <p>
 * Routes incoming WeChat events to either IntelliConnect (local) or xiaozhi (remote)
 * based on the EventKey / sceneStr prefix in subscribe/SCAN events.
 * <p>
 * Routing rules:
 * <ul>
 *   <li>subscribe with EventKey qrscene_login_* -> forward to xiaozhi</li>
 *   <li>SCAN with EventKey login_* -> forward to xiaozhi</li>
 *   <li>UNSUBSCRIBE -> forward to xiaozhi</li>
 *   <li>Everything else -> handle locally in IntelliConnect</li>
 * </ul>
 */
@Component
@Slf4j
public class WxMessageForwarder {

  @Value("${wx.forward.enabled:false}")
  private boolean forwardEnabled;

  @Value("${wx.forward.xiaozhi-url:http://localhost:8002/xiaozhi/wx/event}")
  private String xiaozhiUrl;

  @Value("${wx.msg.token}")
  private String token;

  private final OkHttpClient httpClient = new OkHttpClient();

  /**
   * Check whether a message body should be forwarded to xiaozhi.
   * Supports both JSON and XML message formats.
   *
   * @param bodyInfo the raw POST body (JSON or XML)
   * @return true if the message belongs to xiaozhi and has been forwarded
   */
  public boolean checkAndForward(String bodyInfo) {
    if (!forwardEnabled) {
      return false;
    }

    try {
      String msgType;
      String eventType = null;
      String eventKey = null;

      // Try JSON first, fall back to XML
      if (isJson(bodyInfo)) {
        JSONObject json = JSON.parseObject(bodyInfo);
        msgType = json.getString("MsgType");
        eventType = json.getString("Event");
        eventKey = json.getString("EventKey");
      } else {
        Document document = DocumentHelper.parseText(bodyInfo);
        Element root = document.getRootElement();
        Element msgTypeElem = root.element("MsgType");
        if (msgTypeElem == null) {
          return false;
        }
        msgType = msgTypeElem.getText();
        Element eventElem = root.element("Event");
        if (eventElem != null) {
          eventType = eventElem.getText();
        }
        Element eventKeyElem = root.element("EventKey");
        if (eventKeyElem != null) {
          eventKey = eventKeyElem.getText();
        }
      }

      if (!"event".equalsIgnoreCase(msgType)) {
        // Only event messages may be forwarded; text/image/voice/link/location stay local
        return false;
      }

      boolean shouldForward = false;

      if ("subscribe".equalsIgnoreCase(eventType)) {
        // New subscriber with qrscene_login_* prefix -> xiaozhi
        if (eventKey != null && eventKey.startsWith("qrscene_login_")) {
          shouldForward = true;
        }
      } else if ("SCAN".equals(eventType)) {
        // Already-subscribed user scanning login_* QR code -> xiaozhi
        if (eventKey != null && eventKey.startsWith("login_")) {
          shouldForward = true;
        }
      } else if ("unsubscribe".equalsIgnoreCase(eventType)) {
        // Unsubscribe events always go to xiaozhi
        shouldForward = true;
      }

      if (shouldForward) {
        asyncForward(bodyInfo);
        return true;
      }

    } catch (Exception e) {
      log.error("转发判断异常，消息将本地处理", e);
    }

    return false;
  }

  /**
   * Asynchronously forward the message body to xiaozhi with proper signature parameters.
   */
  private void asyncForward(String bodyInfo) {
    try {
      String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
      String nonce = String.valueOf((int) (Math.random() * 1_000_000_000));
      String signature = generateSignature(token, timestamp, nonce);

      String url = xiaozhiUrl
          + "?signature=" + signature
          + "&timestamp=" + timestamp
          + "&nonce=" + nonce;

      MediaType xmlType = MediaType.parse("application/xml; charset=utf-8");
      RequestBody requestBody = RequestBody.create(xmlType, bodyInfo);
      Request request = new Request.Builder()
          .url(url)
          .post(requestBody)
          .build();

      log.info("转发消息给 xiaozhi: url={}", url);

      httpClient.newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
          log.error("转发消息给 xiaozhi 失败: {}", e.getMessage());
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
          try (ResponseBody body = response.body()) {
            String respBody = body != null ? body.string() : "";
            log.info("xiaozhi 响应: status={}, body={}", response.code(), respBody);
          }
        }
      });

    } catch (Exception e) {
      log.error("构建转发请求异常", e);
    }
  }

  /**
   * Generate WeChat-compatible SHA-1 signature.
   * Algorithm: sort(token, timestamp, nonce) -> concatenate -> SHA-1 hex
   */
  private String generateSignature(String token, String timestamp, String nonce) {
    try {
      String[] arr = {token, timestamp, nonce};
      Arrays.sort(arr);
      StringBuilder sb = new StringBuilder();
      for (String s : arr) {
        sb.append(s);
      }
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hexStr = new StringBuilder();
      for (byte b : digest) {
        String hex = Integer.toHexString(b & 0xff);
        if (hex.length() == 1) {
          hexStr.append('0');
        }
        hexStr.append(hex);
      }
      return hexStr.toString();
    } catch (Exception e) {
      log.error("生成签名异常", e);
      return "";
    }
  }

  /**
   * Simple check to determine if a string is JSON (starts with '{').
   */
  private boolean isJson(String str) {
    if (str == null || str.isBlank()) {
      return false;
    }
    return str.trim().startsWith("{");
  }
}
