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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.rslly.iot.models.InputMessageEntity;
import top.rslly.iot.services.feishu.FeishuOpenApiClient;
import top.rslly.iot.utility.properties.FeishuProperty;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class FeishuSyncService {
  private static final String MODE_DOC = "doc";
  private static final String MODE_WIKI = "wiki";
  private static final int MAX_TITLE_LENGTH = 80;

  private final FeishuProperty feishuProperty;
  private final FeishuOpenApiClient feishuOpenApiClient;

  public FeishuSyncService(FeishuProperty feishuProperty, FeishuOpenApiClient feishuOpenApiClient) {
    this.feishuProperty = feishuProperty;
    this.feishuOpenApiClient = feishuOpenApiClient;
  }

  public FeishuSyncResult syncMessage(InputMessageEntity entity, String contentToSync) {
    validateConfiguration();
    String resolvedContent = resolveContent(entity, contentToSync);
    if (resolvedContent == null || resolvedContent.isBlank()) {
      throw new IllegalArgumentException("Feishu sync content is blank");
    }

    String mode = normalizeMode();
    String title = buildTitle(entity, resolvedContent);
    log.info("starting Feishu sync, messageId={}, dedupeKey={}, sessionId={}, mode={}, title={}", entity.getId(),
        entity.getDedupeKey(), entity.getSessionId(), mode, title);
    String tenantAccessToken =
        feishuOpenApiClient.getTenantAccessToken(feishuProperty.getAppId(), feishuProperty.getAppSecret());
    FeishuOpenApiClient.FeishuDocument document =
        feishuOpenApiClient.createDocument(tenantAccessToken, title, blankToNull(feishuProperty.getFolderToken()));
    feishuOpenApiClient.appendDocumentContent(tenantAccessToken, document.documentId(), resolvedContent);

    long syncedAt = System.currentTimeMillis();
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put("mode", mode);
    reference.put("documentId", document.documentId());
    reference.put("title", document.title());
    if (feishuProperty.getFolderToken() != null && !feishuProperty.getFolderToken().isBlank()) {
      reference.put("folderToken", feishuProperty.getFolderToken());
    }
    String documentUrl = buildWebUrl("docx/" + document.documentId());
    if (documentUrl != null) {
      reference.put("documentUrl", documentUrl);
    }

    if (MODE_WIKI.equals(mode)) {
      FeishuOpenApiClient.FeishuWikiNode wikiNode =
          feishuOpenApiClient.createWikiNode(tenantAccessToken, feishuProperty.getWikiSpaceId(),
              feishuProperty.getWikiParentNodeToken(), title, document.documentId());
      reference.put("wikiSpaceId", wikiNode.spaceId());
      reference.put("wikiNodeToken", wikiNode.nodeToken());
      String wikiUrl = buildWebUrl("wiki/" + wikiNode.nodeToken());
      if (wikiUrl != null) {
        reference.put("wikiUrl", wikiUrl);
      }
    }

    log.info("Feishu sync completed, messageId={}, dedupeKey={}, sessionId={}, mode={}, documentId={}",
        entity.getId(), entity.getDedupeKey(), entity.getSessionId(), mode, document.documentId());
    return new FeishuSyncResult(syncedAt, reference);
  }

  private void validateConfiguration() {
    if (!feishuProperty.isEnabled()) {
      throw new IllegalStateException("Feishu sync is disabled");
    }
    if (isBlank(feishuProperty.getAppId()) || isBlank(feishuProperty.getAppSecret())) {
      throw new IllegalStateException("Feishu appId/appSecret not configured");
    }
    if (MODE_WIKI.equals(normalizeMode())
        && (isBlank(feishuProperty.getWikiSpaceId()) || isBlank(feishuProperty.getWikiParentNodeToken()))) {
      throw new IllegalStateException("Feishu wikiSpaceId/wikiParentNodeToken not configured");
    }
  }

  private String normalizeMode() {
    String mode = feishuProperty.getMode();
    if (mode == null || mode.isBlank()) {
      return MODE_DOC;
    }
    String normalizedMode = mode.trim().toLowerCase(Locale.ROOT);
    if (!MODE_DOC.equals(normalizedMode) && !MODE_WIKI.equals(normalizedMode)) {
      throw new IllegalStateException("Unsupported feishu.mode: " + mode);
    }
    return normalizedMode;
  }

  private String resolveContent(InputMessageEntity entity, String contentToSync) {
    if (contentToSync != null && !contentToSync.isBlank()) {
      return contentToSync.trim();
    }
    if (entity.getNormalizedContent() != null && !entity.getNormalizedContent().isBlank()) {
      return entity.getNormalizedContent().trim();
    }
    if (entity.getRawContent() != null && !entity.getRawContent().isBlank()) {
      return entity.getRawContent().trim();
    }
    return null;
  }

  private String buildTitle(InputMessageEntity entity, String contentToSync) {
    String prefix = blankToNull(feishuProperty.getTitlePrefix());
    String base = firstNonBlank(entity.getNormalizedContent(), entity.getRawContent(), contentToSync);
    String firstLine = Arrays.stream(base.replace("\r\n", "\n").replace('\r', '\n').split("\n"))
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .findFirst()
        .orElse("Input Message " + entity.getId());
    String normalizedFirstLine = firstLine
        .replaceFirst("^#+\\s*", "")
        .replaceFirst("^[-*]\\s*", "")
        .trim();
    if (normalizedFirstLine.isBlank()) {
      normalizedFirstLine = "Input Message " + entity.getId();
    }

    String title = prefix == null ? normalizedFirstLine : prefix + " " + normalizedFirstLine;
    if (title.length() <= MAX_TITLE_LENGTH) {
      return title;
    }
    return title.substring(0, MAX_TITLE_LENGTH - 3) + "...";
  }

  private String buildWebUrl(String path) {
    String webBaseUrl = blankToNull(feishuProperty.getWebBaseUrl());
    if (webBaseUrl == null) {
      return null;
    }
    String normalizedWebBaseUrl =
        webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    return normalizedWebBaseUrl + "/" + path;
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

  private String blankToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public record FeishuSyncResult(long syncedAt, Map<String, Object> reference) {
  }
}
