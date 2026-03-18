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
package top.rslly.iot.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.rslly.iot.models.InputMessageEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * G5: GitHub 同步服务 — 将处理后的消息同步为 GitHub 仓库中的 Markdown 文件。
 * 按日期和文档用途组织目录结构：knowledge/{purpose}/{yyyy-MM-dd}/{title}.md
 *
 * 仅在配置 hub.sync.github.enabled=true 时激活。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "hub.sync.github.enabled", havingValue = "true")
public class GithubSyncService {

  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
  private static final String GITHUB_API_BASE = "https://api.github.com";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @Value("${hub.sync.github.token:}")
  private String githubToken;

  @Value("${hub.sync.github.repo:}")
  private String githubRepo; // owner/repo

  @Value("${hub.sync.github.branch:main}")
  private String branch;

  @Value("${hub.sync.github.base-path:knowledge/}")
  private String basePath;

  private final OkHttpClient httpClient = new OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build();

  /**
   * 同步结果
   */
  public record GithubSyncResult(long syncedAt, Map<String, Object> reference) {
  }

  /**
   * 将消息同步为 GitHub 仓库中的 Markdown 文件。
   */
  public GithubSyncResult syncMessage(InputMessageEntity entity, String contentToSync) {
    validateConfiguration();

    if (contentToSync == null || contentToSync.isBlank()) {
      throw new IllegalArgumentException("GitHub sync content is blank");
    }

    String purpose = entity.getDocumentPurpose() != null ? entity.getDocumentPurpose() : "note";
    String dateStr = Instant.ofEpochMilli(entity.getReceivedAt())
        .atZone(ZoneId.systemDefault())
        .format(DATE_FORMATTER);

    String title = buildTitle(entity, contentToSync);
    String filePath = basePath + purpose + "/" + dateStr + "/" + sanitizeFileName(title) + ".md";

    String markdownContent = buildMarkdownContent(entity, contentToSync, title);

    // 检查文件是否已存在（获取 SHA 用于更新）
    String existingSha = getFileSha(filePath);

    // 创建或更新文件
    createOrUpdateFile(filePath, markdownContent, existingSha,
        "sync: " + entity.getContentType() + " message #" + entity.getId());

    long syncedAt = System.currentTimeMillis();
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put("filePath", filePath);
    reference.put("repo", githubRepo);
    reference.put("branch", branch);
    reference.put("fileUrl", String.format("https://github.com/%s/blob/%s/%s", githubRepo, branch, filePath));

    return new GithubSyncResult(syncedAt, reference);
  }

  private void validateConfiguration() {
    if (githubToken == null || githubToken.isBlank()) {
      throw new IllegalStateException("GitHub token is not configured");
    }
    if (githubRepo == null || githubRepo.isBlank() || !githubRepo.contains("/")) {
      throw new IllegalStateException("GitHub repo is not configured (expected owner/repo format)");
    }
  }

  private String buildTitle(InputMessageEntity entity, String content) {
    // 优先使用 AI 摘要
    if (entity.getAiSummary() != null && !entity.getAiSummary().isBlank()) {
      String summary = entity.getAiSummary();
      return summary.length() > 60 ? summary.substring(0, 60) : summary;
    }
    // 否则取内容前 60 字
    String text = content.length() > 60 ? content.substring(0, 60) : content;
    return text.replaceAll("[\\r\\n]+", " ").trim();
  }

  private String sanitizeFileName(String name) {
    return name.replaceAll("[^\\w\\u4e00-\\u9fa5\\-.]", "_")
        .replaceAll("_{2,}", "_")
        .replaceAll("^_|_$", "");
  }

  private String buildMarkdownContent(InputMessageEntity entity, String content, String title) {
    StringBuilder sb = new StringBuilder();
    sb.append("# ").append(title).append("\n\n");

    // 元数据
    sb.append("| 字段 | 值 |\n|------|-----|\n");
    sb.append("| 来源 | ").append(entity.getSourceType()).append(" |\n");
    sb.append("| 类型 | ").append(entity.getContentType()).append(" |\n");
    if (entity.getContentCategory() != null) {
      sb.append("| 分类 | ").append(entity.getContentCategory()).append(" |\n");
    }
    if (entity.getDocumentPurpose() != null) {
      sb.append("| 用途 | ").append(entity.getDocumentPurpose()).append(" |\n");
    }
    sb.append("| 时间 | ").append(
            Instant.ofEpochMilli(entity.getReceivedAt()).atZone(ZoneId.systemDefault()))
        .append(" |\n");
    sb.append("\n---\n\n");

    // AI 摘要
    if (entity.getAiSummary() != null && !entity.getAiSummary().isBlank()) {
      sb.append("> **摘要**: ").append(entity.getAiSummary()).append("\n\n");
    }

    // 正文
    sb.append("## 内容\n\n").append(content).append("\n");

    // 标签
    if (entity.getContentTags() != null && !entity.getContentTags().isBlank()) {
      sb.append("\n---\n\n**标签**: ");
      for (String tag : entity.getContentTags().split(",")) {
        sb.append("`").append(tag.trim()).append("` ");
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * 获取文件的 SHA（如果存在），用于更新操作
   */
  private String getFileSha(String filePath) {
    String url = GITHUB_API_BASE + "/repos/" + githubRepo + "/contents/" + filePath + "?ref=" + branch;
    Request request = new Request.Builder()
        .url(url)
        .header("Authorization", "Bearer " + githubToken)
        .header("Accept", "application/vnd.github.v3+json")
        .get()
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        JSONObject body = JSON.parseObject(response.body().string());
        return body.getString("sha");
      }
    } catch (IOException e) {
      log.debug("File not found on GitHub (expected for new files): {}", filePath);
    }
    return null;
  }

  /**
   * 通过 GitHub Contents API 创建或更新文件
   */
  private void createOrUpdateFile(String filePath, String content, String sha, String commitMessage) {
    String url = GITHUB_API_BASE + "/repos/" + githubRepo + "/contents/" + filePath;

    JSONObject payload = new JSONObject();
    payload.put("message", commitMessage);
    payload.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
    payload.put("branch", branch);
    if (sha != null) {
      payload.put("sha", sha);
    }

    Request request = new Request.Builder()
        .url(url)
        .header("Authorization", "Bearer " + githubToken)
        .header("Accept", "application/vnd.github.v3+json")
        .put(RequestBody.create(payload.toJSONString(), JSON_MEDIA_TYPE))
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String responseBody = response.body() != null ? response.body().string() : "";
        throw new IllegalStateException(
            "GitHub API error: " + response.code() + " " + response.message() + " - " + responseBody);
      }
      log.info("GitHub file synced: path={}, sha={}", filePath, sha != null ? "updated" : "created");
    } catch (IOException e) {
      throw new RuntimeException("GitHub API call failed: " + e.getMessage(), e);
    }
  }
}
