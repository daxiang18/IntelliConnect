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
package top.rslly.iot.utility.ai.voice.ASR;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 火山引擎（豆包）大模型录音文件识别极速版 ASR 服务实现。
 * <p>
 * 接口文档：https://www.volcengine.com/docs/6561/1631584 （录音文件极速版识别HTTP） 一次 HTTP 请求同步返回识别结果，无需
 * submit/query 轮询，适合小智 VAD 切段后的短音频识别。 鉴权使用旧版控制台的 APP ID + Access Token（X-Api-App-Key /
 * X-Api-Access-Key）， 资源 ID 默认 volc.bigasr.auc_turbo。
 */
@Slf4j
@Component
public class VolcanoAsrService implements AsrService {

  private static final String API_URL =
      "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash";
  private static final String STATUS_HEADER = "X-Api-Status-Code";
  private static final String MESSAGE_HEADER = "X-Api-Message";
  private static final String LOGID_HEADER = "X-Tt-Logid";
  private static final String STATUS_SUCCESS = "20000000";
  private static final String STATUS_SILENT_AUDIO = "20000003";
  private static final int CONNECT_TIMEOUT_MS = 5000;
  private static final int READ_TIMEOUT_MS = 60000;
  private static final List<String> SUPPORTED_FORMATS =
      List.of("raw", "wav", "mp3", "ogg", "pcm", "spx", "amr", "aac", "m4a");

  @Value("${ai.volcano.asr.app-id:}")
  private String appId;

  @Value("${ai.volcano.asr.access-token:}")
  private String accessToken;

  @Value("${ai.volcano.asr.resource-id:volc.bigasr.auc_turbo}")
  private String resourceId;

  @Value("${ai.volcano.asr.enable-punc:true}")
  private boolean enablePunc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String getText(String url) {
    if (url == null || url.isEmpty()) {
      return "语音识别失败";
    }
    try {
      ObjectNode audio = objectMapper.createObjectNode();
      audio.put("url", url);
      audio.put("format", resolveUrlFormat(url));
      String text = recognize(audio);
      if (text == null) {
        return "语音识别失败";
      }
      log.debug("语音转换结果{}", text);
      return text;
    } catch (Exception e) {
      log.error("语音识别失败", e);
      return "语音识别失败";
    }
  }

  @Override
  public String getTextRealtime(File file, int sampleRate, String format) {
    if (file == null || !file.exists() || file.length() == 0) {
      return "";
    }
    try {
      byte[] audioBytes = Files.readAllBytes(file.toPath());
      ObjectNode audio = objectMapper.createObjectNode();
      audio.put("data", Base64.getEncoder().encodeToString(audioBytes));
      // 小智链路传入的是 Opus 解码后的裸 PCM（16bit 单声道），对应火山的 raw 格式
      if ("pcm".equalsIgnoreCase(format)) {
        audio.put("format", "raw");
        audio.put("codec", "raw");
        audio.put("rate", sampleRate);
        audio.put("bits", 16);
        audio.put("channel", 1);
      } else {
        audio.put("format", normalizeFormat(format));
        audio.put("rate", sampleRate);
      }
      String text = recognize(audio);
      return text == null ? "" : text;
    } catch (Exception e) {
      log.error("语音识别失败{}", e.getMessage());
      return "";
    }
  }

  /**
   * 调用极速版识别接口并提取文本。
   *
   * @param audio 请求体的 audio 节点（url 或 data 二选一）
   * @return 识别文本；静音音频返回空串；失败返回 null
   */
  private String recognize(ObjectNode audio) throws Exception {
    if (appId == null || appId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
      log.error("火山引擎ASR未配置app-id或access-token");
      return null;
    }
    ObjectNode root = objectMapper.createObjectNode();
    ObjectNode user = root.putObject("user");
    user.put("uid", appId);
    root.set("audio", audio);
    ObjectNode request = root.putObject("request");
    request.put("model_name", "bigmodel");
    request.put("enable_punc", enablePunc);
    byte[] requestBody = objectMapper.writeValueAsBytes(root);

    HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
    try {
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("X-Api-App-Key", appId);
      connection.setRequestProperty("X-Api-Access-Key", accessToken);
      connection.setRequestProperty("X-Api-Resource-Id", resourceId);
      connection.setRequestProperty("X-Api-Request-Id", UUID.randomUUID().toString());
      connection.setRequestProperty("X-Api-Sequence", "-1");
      connection.setDoOutput(true);
      connection.getOutputStream().write(requestBody);

      int responseCode = connection.getResponseCode();
      String statusCode = connection.getHeaderField(STATUS_HEADER);
      String logId = connection.getHeaderField(LOGID_HEADER);
      if (STATUS_SILENT_AUDIO.equals(statusCode)) {
        log.debug("火山引擎ASR检测到静音音频: logid={}", logId);
        return "";
      }
      if (responseCode != 200 || !STATUS_SUCCESS.equals(statusCode)) {
        log.error("火山引擎ASR请求失败: httpCode={}, statusCode={}, message={}, logid={}",
            responseCode, statusCode, connection.getHeaderField(MESSAGE_HEADER), logId);
        return null;
      }
      String responseBody = readBody(connection.getInputStream());
      JsonNode result = objectMapper.readTree(responseBody).path("result");
      JsonNode textNode = result.path("text");
      if (textNode.isMissingNode() || textNode.isNull()) {
        log.error("火山引擎ASR响应缺少result.text: logid={}, body={}", logId, responseBody);
        return null;
      }
      return textNode.asText();
    } finally {
      connection.disconnect();
    }
  }

  private String readBody(InputStream inputStream) throws Exception {
    StringBuilder body = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        body.append(line);
      }
    }
    return body.toString();
  }

  private String normalizeFormat(String format) {
    if (format == null || format.isEmpty()) {
      return "wav";
    }
    String normalized = format.toLowerCase(Locale.ROOT);
    return SUPPORTED_FORMATS.contains(normalized) ? normalized : "wav";
  }

  private String resolveUrlFormat(String url) {
    String path = url;
    int queryIndex = path.indexOf('?');
    if (queryIndex >= 0) {
      path = path.substring(0, queryIndex);
    }
    int dotIndex = path.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex < path.length() - 1) {
      String extension = path.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
      if (SUPPORTED_FORMATS.contains(extension)) {
        return extension;
      }
    }
    return "mp3";
  }

}
