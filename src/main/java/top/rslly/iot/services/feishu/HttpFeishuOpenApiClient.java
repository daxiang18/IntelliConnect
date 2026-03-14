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
package top.rslly.iot.services.feishu;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.rslly.iot.utility.properties.FeishuProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "feishu", name = "enabled", havingValue = "true")
public class HttpFeishuOpenApiClient implements FeishuOpenApiClient {
  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
  private static final int MAX_BLOCK_TEXT_LENGTH = 1000;
  private static final int MAX_BLOCK_BATCH_SIZE = 20;
  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final long INITIAL_RETRY_DELAY_MILLIS = 200L;
  private static final long MAX_RETRY_DELAY_MILLIS = 1_000L;

  private final FeishuProperty feishuProperty;
  private final Call.Factory callFactory;
  private final RetrySleeper retrySleeper;

  public HttpFeishuOpenApiClient(FeishuProperty feishuProperty) {
    this(feishuProperty, new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(), Thread::sleep);
  }

  HttpFeishuOpenApiClient(FeishuProperty feishuProperty, Call.Factory callFactory, RetrySleeper retrySleeper) {
    this.feishuProperty = feishuProperty;
    this.callFactory = callFactory;
    this.retrySleeper = retrySleeper;
  }

  @Override
  public String getTenantAccessToken(String appId, String appSecret) {
    JSONObject payload = new JSONObject();
    payload.put("app_id", appId);
    payload.put("app_secret", appSecret);
    JSONObject data =
        postForData("auth/v3/tenant_access_token/internal", null, payload, "get tenant access token");
    String tenantAccessToken =
        firstNonBlank(data.getString("tenant_access_token"), data.getString("tenantAccessToken"));
    if (tenantAccessToken == null || tenantAccessToken.isBlank()) {
      throw new IllegalStateException("Feishu tenant access token missing in response");
    }
    return tenantAccessToken;
  }

  @Override
  public FeishuDocument createDocument(String tenantAccessToken, String title, String folderToken) {
    JSONObject payload = new JSONObject();
    payload.put("title", title);
    if (folderToken != null && !folderToken.isBlank()) {
      payload.put("folder_token", folderToken);
    }
    JSONObject data = postForData("docx/v1/documents", tenantAccessToken, payload, "create document");
    JSONObject document = data.getJSONObject("document");
    if (document == null) {
      document = data;
    }
    String documentId = firstNonBlank(document.getString("document_id"), document.getString("documentId"));
    if (documentId == null || documentId.isBlank()) {
      throw new IllegalStateException("Feishu document id missing in response");
    }
    String documentTitle = firstNonBlank(document.getString("title"), title);
    return new FeishuDocument(documentId, documentTitle);
  }

  @Override
  public void appendDocumentContent(String tenantAccessToken, String documentId, String content) {
    List<String> paragraphs = splitContent(content);
    if (paragraphs.isEmpty()) {
      return;
    }

    for (int start = 0; start < paragraphs.size(); start += MAX_BLOCK_BATCH_SIZE) {
      int end = Math.min(start + MAX_BLOCK_BATCH_SIZE, paragraphs.size());
      JSONArray children = new JSONArray();
      for (String paragraph : paragraphs.subList(start, end)) {
        JSONObject textRun = new JSONObject();
        textRun.put("content", paragraph);

        JSONObject element = new JSONObject();
        element.put("text_run", textRun);

        JSONArray elements = new JSONArray();
        elements.add(element);

        JSONObject paragraphNode = new JSONObject();
        paragraphNode.put("elements", elements);

        JSONObject child = new JSONObject();
        child.put("block_type", 2);
        child.put("paragraph", paragraphNode);
        children.add(child);
      }

      JSONObject payload = new JSONObject();
      payload.put("children", children);
      postForData(String.format(Locale.ROOT,
          "docx/v1/documents/%s/blocks/%s/children?document_revision_id=-1", documentId, documentId),
          tenantAccessToken, payload, "append document content");
    }
  }

  @Override
  public FeishuWikiNode createWikiNode(String tenantAccessToken, String spaceId, String parentNodeToken,
      String title, String documentId) {
    JSONObject payload = new JSONObject();
    payload.put("parent_node_token", parentNodeToken);
    payload.put("node_type", "origin");
    payload.put("obj_type", "docx");
    payload.put("obj_token", documentId);
    payload.put("title", title);

    JSONObject data = postForData(String.format(Locale.ROOT, "wiki/v2/spaces/%s/nodes", spaceId),
        tenantAccessToken, payload, "create wiki node");
    JSONObject node = data.getJSONObject("node");
    if (node == null) {
      node = data;
    }
    String nodeToken = firstNonBlank(node.getString("node_token"), node.getString("nodeToken"));
    if (nodeToken == null || nodeToken.isBlank()) {
      throw new IllegalStateException("Feishu wiki node token missing in response");
    }
    String nodeTitle = firstNonBlank(node.getString("title"), title);
    return new FeishuWikiNode(spaceId, nodeToken, nodeTitle);
  }

  private JSONObject postForData(String path, String tenantAccessToken, JSONObject payload, String action) {
    Request.Builder requestBuilder = new Request.Builder()
        .url(buildApiUrl(path))
        .post(RequestBody.create(JSON.toJSONString(payload), JSON_MEDIA_TYPE));
    if (tenantAccessToken != null && !tenantAccessToken.isBlank()) {
      requestBuilder.header("Authorization", "Bearer " + tenantAccessToken);
    }
    Request request = requestBuilder.build();

    long retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
    for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
      try (Response response = callFactory.newCall(request).execute()) {
        String body = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
          if (shouldRetry(response.code()) && attempt < MAX_RETRY_ATTEMPTS) {
            log.warn("Transient Feishu {} failure on attempt {}/{}, httpStatus={}, retrying in {}ms", action,
                attempt, MAX_RETRY_ATTEMPTS, response.code(), retryDelayMillis);
            retryDelayMillis = sleepBeforeRetry(action, retryDelayMillis);
            continue;
          }
          throw new IllegalStateException(
              "Feishu " + action + " failed with httpStatus=" + response.code() + ", body=" + abbreviate(body));
        }
        JSONObject jsonObject = JSON.parseObject(body);
        if (jsonObject == null) {
          throw new IllegalStateException("Feishu " + action + " returned empty body");
        }
        Integer code = jsonObject.getInteger("code");
        if (code != null && code != 0) {
          String message = firstNonBlank(jsonObject.getString("msg"), jsonObject.getString("message"));
          throw new IllegalStateException(
              "Feishu " + action + " failed with code=" + code + ", msg=" + abbreviate(message));
        }
        JSONObject data = jsonObject.getJSONObject("data");
        return data == null ? new JSONObject() : data;
      } catch (IOException ex) {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
          throw new IllegalStateException("Feishu " + action + " request failed", ex);
        }
        log.warn("Transient Feishu {} request failure on attempt {}/{}, retrying in {}ms, cause={}, message={}",
            action, attempt, MAX_RETRY_ATTEMPTS, retryDelayMillis, ex.getClass().getSimpleName(),
            abbreviate(ex.getMessage()));
        retryDelayMillis = sleepBeforeRetry(action, retryDelayMillis);
      }
    }
    throw new IllegalStateException("Feishu " + action + " request failed");
  }

  private String buildApiUrl(String path) {
    String baseUrl = feishuProperty.getApiBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("feishu.api-base-url is blank");
    }
    String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return normalizedBaseUrl + "/" + path;
  }

  private List<String> splitContent(String content) {
    List<String> paragraphs = new ArrayList<>();
    if (content == null || content.isBlank()) {
      return paragraphs;
    }

    String normalizedContent = content.replace("\r\n", "\n").replace('\r', '\n').trim();
    if (normalizedContent.isBlank()) {
      return paragraphs;
    }

    for (String paragraph : normalizedContent.split("\n{2,}")) {
      String normalizedParagraph = paragraph == null ? "" : paragraph.trim();
      if (normalizedParagraph.isBlank()) {
        continue;
      }
      for (String line : normalizedParagraph.split("\n")) {
        String normalizedLine = line == null ? "" : line.trim();
        if (normalizedLine.isBlank()) {
          continue;
        }
        paragraphs.addAll(splitByLength(normalizedLine, MAX_BLOCK_TEXT_LENGTH));
      }
    }
    return paragraphs;
  }

  private List<String> splitByLength(String content, int maxLength) {
    List<String> parts = new ArrayList<>();
    if (content == null || content.isBlank()) {
      return parts;
    }
    for (int start = 0; start < content.length(); start += maxLength) {
      int end = Math.min(start + maxLength, content.length());
      parts.add(content.substring(start, end));
    }
    return parts;
  }

  private String abbreviate(String content) {
    if (content == null) {
      return null;
    }
    if (content.length() <= 300) {
      return content;
    }
    return content.substring(0, 297) + "...";
  }

  private boolean shouldRetry(int httpStatus) {
    return httpStatus == 429 || (httpStatus >= 500 && httpStatus < 600);
  }

  private long sleepBeforeRetry(String action, long delayMillis) {
    try {
      retrySleeper.sleep(delayMillis);
      return Math.min(delayMillis * 2L, MAX_RETRY_DELAY_MILLIS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Feishu " + action + " retry interrupted", ex);
    }
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  @FunctionalInterface
  interface RetrySleeper {
    void sleep(long delayMillis) throws InterruptedException;
  }
}
