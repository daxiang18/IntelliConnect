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
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.rslly.iot.dao.InputMessageRepository;
import top.rslly.iot.models.InputMessageEntity;
import top.rslly.iot.param.request.AgentLongMemory;
import top.rslly.iot.param.request.InputAttachmentParam;
import top.rslly.iot.param.request.InputMessageCreateParam;
import top.rslly.iot.param.request.InputMessagePromoteParam;
import top.rslly.iot.param.request.InputMessageRecallParam;
import top.rslly.iot.param.response.InputMessageRecallItemResponse;
import top.rslly.iot.param.response.InputMessageResponse;
import top.rslly.iot.services.agent.AgentLongMemoryServiceImpl;
import top.rslly.iot.services.agent.AiService;
import top.rslly.iot.utility.input.InputContentAutoTagger;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.ai.rag.RagUtility;
import top.rslly.iot.utility.ai.voice.ASR.AsrServiceFactory;
import top.rslly.iot.utility.input.UrlContentNormalizer;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InputMessageServiceImpl implements InputMessageService {
  private static final String STATUS_RECEIVED = "received";
  private static final String STATUS_PROCESSING = "processing";
  private static final String STATUS_INGESTED = "ingested";
  private static final String STATUS_FAILED = "failed";
  private static final String SOURCE_TYPE_WECHAT = "wechat";
  private static final String CONTENT_TYPE_TEXT = "text";
  private static final String CONTENT_TYPE_IMAGE = "image";
  private static final String CONTENT_TYPE_VOICE = "voice";
  private static final String CONTENT_TYPE_URL = "url";
  private static final String SYNC_TARGET_FEISHU = "feishu";
  private static final String SYNC_STATUS_NOT_REQUESTED = "not_requested";
  private static final String SYNC_STATUS_PENDING = "pending";
  private static final String SYNC_STATUS_SYNCED = "synced";
  private static final String SYNC_STATUS_FAILED = "failed";
  private static final int DEFAULT_SESSION_QUERY_PAGE = 0;
  private static final int DEFAULT_SESSION_QUERY_SIZE = 50;
  private static final int MAX_SESSION_QUERY_SIZE = 100;
  private static final int DEFAULT_STALE_PROCESSING_MINUTES = 30;
  private static final int DEFAULT_STALE_PROCESSING_LIMIT = 20;
  private static final int MAX_STALE_PROCESSING_LIMIT = 100;
  private static final String WECHAT_IMAGE_ATTACHMENT_NAME = "image";
  private static final String WECHAT_VOICE_ATTACHMENT_NAME = "voice";
  private static final String WECHAT_IMAGE_MIME_TYPE = "image/jpeg";
  private static final String WECHAT_VOICE_MIME_TYPE = "audio/amr";
  private static final String IMAGE_SEARCH_PROMPT =
      "请提取这张图片里对检索最有帮助的信息，包括可见文字、主要物体、场景和关键线索，使用简洁中文输出。";
  private static final Set<String> SUPPORTED_SYNC_TARGETS = Set.of(SYNC_TARGET_FEISHU);

  @Resource
  private InputMessageRepository inputMessageRepository;
  @Autowired
  private EmbeddingModel embeddingModel;
  @Autowired
  private EmbeddingStore<TextSegment> knowledgeChatEmbeddingStore;
  @Autowired
  @Qualifier("taskExecutor")
  private TaskExecutor taskExecutor;
  @Autowired
  private AgentLongMemoryServiceImpl agentLongMemoryService;
  @Autowired
  private AiService aiService;
  @Autowired
  private UrlContentNormalizer urlContentNormalizer;
  @Autowired
  private InputContentAutoTagger inputContentAutoTagger;
  @Autowired(required = false)
  private FeishuSyncService feishuSyncService;
  @Autowired
  private AsrServiceFactory asrServiceFactory;
  @Autowired
  private Validator validator;

  private String resolveUsername(String token) {
    String tokenDeal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    return JwtTokenUtil.getUsername(tokenDeal);
  }

  private String resolveExceptionMessage(Exception exception) {
    if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
      return exception == null ? "" : exception.getClass().getSimpleName();
    }
    return exception.getMessage();
  }

  private String describeConstraintViolations(List<String> violations) {
    return violations.stream()
        .sorted()
        .collect(Collectors.joining("; "));
  }

  private String describeConstraintViolations(
      java.util.Set<ConstraintViolation<InputMessageCreateParam>> violations) {
    return describeConstraintViolations(violations.stream()
        .map(violation -> {
          String propertyPath = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
          return (propertyPath == null || propertyPath.isBlank() ? "request" : propertyPath)
              + ": " + violation.getMessage();
        })
        .collect(Collectors.toList()));
  }

  private boolean isValidAttachmentUrl(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      URI attachmentUri = new URI(url.trim());
      String scheme = attachmentUri.getScheme();
      String host = attachmentUri.getHost();
      return scheme != null
          && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          && host != null && !host.isBlank();
    } catch (Exception ex) {
      return false;
    }
  }

  private String validateCreateMessagePayload(InputMessageCreateParam inputMessageCreateParam) {
    if (inputMessageCreateParam == null) {
      return "request body is null";
    }
    var violations = validator.validate(inputMessageCreateParam);
    if (!violations.isEmpty()) {
      return describeConstraintViolations(violations);
    }
    List<InputAttachmentParam> attachments = inputMessageCreateParam.getAttachments();
    if (attachments == null) {
      return null;
    }
    List<String> manualValidationErrors = new java.util.ArrayList<>();
    for (int index = 0; index < attachments.size(); index++) {
      InputAttachmentParam attachment = attachments.get(index);
      if (attachment != null && !isValidAttachmentUrl(attachment.getUrl())) {
        manualValidationErrors.add("attachments[" + index + "].url: 必须是可解析的 http/https 地址");
      }
    }
    return manualValidationErrors.isEmpty() ? null : describeConstraintViolations(manualValidationErrors);
  }

  private void updateStatus(long id, String status) {
    inputMessageRepository.findById(id).ifPresent(entity -> {
      applyStatus(entity, status, null);
      inputMessageRepository.save(entity);
    });
  }

  private void updateStatusIfAttemptMatches(long id, String status, String expectedAttemptToken) {
    inputMessageRepository.findById(id).ifPresent(entity -> {
      if (expectedAttemptToken != null
          && !expectedAttemptToken.equals(entity.getProcessingAttemptToken())) {
        log.warn(
            "stale attempt discarded, skipping status finalization, id={}, expectedToken={}, currentToken={}",
            id, expectedAttemptToken, entity.getProcessingAttemptToken());
        return;
      }
      applyStatus(entity, status, null);
      inputMessageRepository.save(entity);
    });
  }

  private void applyStatus(InputMessageEntity entity, String status, Long processingStartedAt) {
    entity.setStatus(status);
    if (STATUS_PROCESSING.equals(status)) {
      entity.setProcessingStartedAt(processingStartedAt == null ? System.currentTimeMillis() : processingStartedAt);
      entity.setProcessingAttemptToken(UUID.randomUUID().toString());
      entity.setProcessingAttemptCount(entity.getProcessingAttemptCount() + 1);
      return;
    }
    entity.setProcessingStartedAt(null);
    entity.setProcessingAttemptToken(null);
  }

  private String resolvePromotedMemoryValue(InputMessageEntity entity, InputMessagePromoteParam param) {
    String memoryValue = param.getMemoryValue();
    if (memoryValue == null || memoryValue.isBlank()) {
      memoryValue = entity.getNormalizedContent();
    }
    if (memoryValue == null || memoryValue.isBlank()) {
      memoryValue = entity.getRawContent();
    }
    return memoryValue;
  }

  private boolean isUrlContentType(String contentType) {
    return CONTENT_TYPE_URL.equalsIgnoreCase(contentType);
  }

  private String resolveUrlContentForIngest(InputMessageEntity entity) {
    var normalizedResult = urlContentNormalizer.normalize(entity.getRawContent());
    String normalizedContent = normalizedResult.normalizedContent();
    String fallbackSummary = entity.getNormalizedContent();
    if (!normalizedResult.success() && fallbackSummary != null && !fallbackSummary.isBlank()
        && !fallbackSummary.equals(entity.getRawContent()) && !normalizedContent.contains(fallbackSummary)) {
      normalizedContent = normalizedContent + "\n\n## 原始链接消息\n" + fallbackSummary;
    }
    entity.setNormalizedContent(normalizedContent);
    inputMessageRepository.save(entity);
    if (normalizedResult.success()) {
      log.info(
          "url content normalized, messageId={}, dedupeKey={}, sessionId={}, sourceUrl={}, title={}, contentLength={}",
          entity.getId(), entity.getDedupeKey(), entity.getSessionId(), entity.getRawContent(),
          normalizedResult.title(), normalizedContent == null ? 0 : normalizedContent.length());
    }
    if (!normalizedResult.success()) {
      log.warn("url content normalization failed, messageId={}, dedupeKey={}, sessionId={}, sourceUrl={}, reason={}",
          entity.getId(), entity.getDedupeKey(), entity.getSessionId(), entity.getRawContent(),
          normalizedResult.failureReason());
    }
    return normalizedContent;
  }

  private String extractVisionText(String aiVisionResponse) {
    if (aiVisionResponse == null || aiVisionResponse.isBlank()) {
      return null;
    }
    if (!aiVisionResponse.trim().startsWith("{")) {
      return null;
    }
    try {
      JSONObject visionResult = JSON.parseObject(aiVisionResponse);
      if (visionResult == null || !Boolean.TRUE.equals(visionResult.getBoolean("success"))) {
        return null;
      }
      String text = visionResult.getString("text");
      return text == null || text.isBlank() ? null : text.trim();
    } catch (Exception e) {
      log.warn("image vision response parse failed, errorType={}", e.getClass().getSimpleName());
      return null;
    }
  }

  private String buildImageSearchableContent(String imageUrl, String existingContent, String visionText) {
    String normalizedExistingContent = existingContent == null ? null : existingContent.trim();
    boolean hasDistinctExistingContent = normalizedExistingContent != null && !normalizedExistingContent.isBlank()
        && !normalizedExistingContent.equals(imageUrl);
    StringBuilder contentBuilder = new StringBuilder();
    if (hasDistinctExistingContent) {
      contentBuilder.append("## 原始图片描述\n")
          .append(normalizedExistingContent)
          .append("\n\n");
    }
    contentBuilder.append("## 图片内容摘要\n")
        .append(visionText.trim());
    if (imageUrl != null && !imageUrl.isBlank()) {
      contentBuilder.append("\n\n## 原始图片链接\n")
          .append(imageUrl);
    }
    return contentBuilder.toString();
  }

  private String resolveImageContentForIngest(InputMessageEntity entity) {
    String imageUrl = entity.getRawContent();
    String fallbackContent = entity.getNormalizedContent();
    if (fallbackContent == null || fallbackContent.isBlank()) {
      fallbackContent = imageUrl;
    }
    if (imageUrl == null || imageUrl.isBlank()) {
      return fallbackContent;
    }

    String aiVisionResponse = aiService.getAiVisionIntent(IMAGE_SEARCH_PROMPT, imageUrl);
    String visionText = extractVisionText(aiVisionResponse);
    if (visionText == null || visionText.isBlank()) {
      log.warn("image vision enrichment skipped, messageId={}, dedupeKey={}, sessionId={}",
          entity.getId(), entity.getDedupeKey(), entity.getSessionId());
      return fallbackContent;
    }

    String enrichedContent = buildImageSearchableContent(imageUrl, entity.getNormalizedContent(), visionText);
    entity.setNormalizedContent(enrichedContent);
    inputMessageRepository.save(entity);
    log.info("image content enriched, messageId={}, dedupeKey={}, sessionId={}, contentLength={}",
        entity.getId(), entity.getDedupeKey(), entity.getSessionId(), enrichedContent.length());
    return enrichedContent;
  }

  private String resolveVoiceContentForIngest(InputMessageEntity entity) {
    String audioUrl = entity.getRawContent();
    String existingTranscript = entity.getNormalizedContent();

    // Upstream already provided a distinct transcript — preserve it without re-transcribing.
    boolean hasDistinctTranscript = existingTranscript != null && !existingTranscript.isBlank()
        && !existingTranscript.equals(audioUrl);
    if (hasDistinctTranscript) {
      return existingTranscript;
    }

    if (audioUrl == null || audioUrl.isBlank()) {
      log.warn("voice transcription skipped: no audio URL, messageId={}, dedupeKey={}, sessionId={}",
          entity.getId(), entity.getDedupeKey(), entity.getSessionId());
      return existingTranscript != null && !existingTranscript.isBlank() ? existingTranscript : audioUrl;
    }

    String transcript = asrServiceFactory.getService().getText(audioUrl);
    if (transcript == null || transcript.isBlank()) {
      log.warn("voice transcription returned blank, falling back to audioUrl, messageId={}, dedupeKey={}, sessionId={}",
          entity.getId(), entity.getDedupeKey(), entity.getSessionId());
      return audioUrl;
    }

    entity.setNormalizedContent(transcript);
    inputMessageRepository.save(entity);
    log.info("voice content transcribed in-pipeline, messageId={}, dedupeKey={}, sessionId={}, transcriptLength={}",
        entity.getId(), entity.getDedupeKey(), entity.getSessionId(), transcript.length());
    return transcript;
  }

  private InputContentAutoTagger.AutoTagResult resolveAutoTagResult(String contentType, String rawContent,
      String normalizedContent) {
    return inputContentAutoTagger.analyze(contentType, rawContent, normalizedContent);
  }

  private String normalizeSyncTargets(String rawSyncTargets) {
    if (rawSyncTargets == null || rawSyncTargets.isBlank()) {
      return null;
    }
    LinkedHashSet<String> normalizedTargets = new LinkedHashSet<>();
    for (String syncTarget : inputContentAutoTagger.parseTags(rawSyncTargets)) {
      String normalizedTarget = syncTarget.toLowerCase(Locale.ROOT);
      if (!SUPPORTED_SYNC_TARGETS.contains(normalizedTarget)) {
        throw new IllegalArgumentException("Unsupported sync target: " + syncTarget);
      }
      normalizedTargets.add(normalizedTarget);
    }
    return normalizedTargets.isEmpty() ? null : String.join(",", normalizedTargets);
  }

  private String resolveSyncStatus(String syncTargets, String syncStatus) {
    if (syncStatus != null && !syncStatus.isBlank()) {
      return syncStatus;
    }
    return syncTargets == null || syncTargets.isBlank() ? SYNC_STATUS_NOT_REQUESTED : SYNC_STATUS_PENDING;
  }

  private boolean shouldSyncToTarget(String syncTargets, String target) {
    return inputContentAutoTagger.parseTags(syncTargets).stream()
        .map(tag -> tag.toLowerCase(Locale.ROOT))
        .anyMatch(target::equals);
  }

  private JSONObject parseExternalReferences(String externalReferencesJson) {
    if (externalReferencesJson == null || externalReferencesJson.isBlank()) {
      return new JSONObject();
    }
    JSONObject externalReferences = JSON.parseObject(externalReferencesJson);
    return externalReferences == null ? new JSONObject() : externalReferences;
  }

  private String resolveAggregateSyncStatus(String syncTargets, JSONObject externalReferences) {
    List<String> targets = inputContentAutoTagger.parseTags(syncTargets);
    if (targets.isEmpty()) {
      return SYNC_STATUS_NOT_REQUESTED;
    }

    boolean allSynced = true;
    for (String target : targets) {
      JSONObject targetReference = externalReferences.getJSONObject(target);
      String targetStatus = targetReference == null ? null : targetReference.getString("status");
      if (SYNC_STATUS_FAILED.equals(targetStatus)) {
        return SYNC_STATUS_FAILED;
      }
      if (!SYNC_STATUS_SYNCED.equals(targetStatus)) {
        allSynced = false;
      }
    }
    return allSynced ? SYNC_STATUS_SYNCED : SYNC_STATUS_PENDING;
  }

  private void updateTargetSyncState(long id, String target, String targetStatus, Long syncedAt,
      Map<String, Object> targetReferenceFields) {
    inputMessageRepository.findById(id).ifPresent(entity -> {
      JSONObject externalReferences = parseExternalReferences(entity.getExternalReferencesJson());
      JSONObject targetReference = externalReferences.getJSONObject(target);
      if (targetReference == null) {
        targetReference = new JSONObject();
      }
      if (targetReferenceFields != null) {
        targetReferenceFields.forEach(targetReference::put);
      }
      targetReference.put("status", targetStatus);
      targetReference.put("updatedAt", System.currentTimeMillis());
      externalReferences.put(target, targetReference);
      entity.setExternalReferencesJson(externalReferences.toJSONString());
      entity.setSyncStatus(resolveAggregateSyncStatus(entity.getSyncTargets(), externalReferences));
      if (syncedAt != null) {
        entity.setSyncedAt(syncedAt);
      }
      inputMessageRepository.save(entity);
    });
  }

  private Map<String, Object> buildTargetSyncFailureReference(Exception exception) {
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put("errorType", exception.getClass().getSimpleName());
    reference.put("errorMessage",
        exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage());
    return reference;
  }

  private InputMessageResponse toResponse(InputMessageEntity entity) {
    InputContentAutoTagger.AutoTagResult autoTagResult =
        resolveAutoTagResult(entity.getContentType(), entity.getRawContent(), entity.getNormalizedContent());
    String syncTargets = entity.getSyncTargets();
    Long processingStartedAt = entity.getProcessingStartedAt();
    if (STATUS_PROCESSING.equals(entity.getStatus()) && processingStartedAt == null) {
      processingStartedAt = entity.getReceivedAt();
    }
    InputMessageResponse response = new InputMessageResponse();
    response.setId(entity.getId());
    response.setSourceType(entity.getSourceType());
    response.setSourceAccountId(entity.getSourceAccountId());
    response.setSessionId(entity.getSessionId());
    response.setSenderId(entity.getSenderId());
    response.setContentType(entity.getContentType());
    response.setRawContent(entity.getRawContent());
    response.setNormalizedContent(entity.getNormalizedContent());
    response.setAttachmentsJson(entity.getAttachmentsJson());
    response.setDedupeKey(entity.getDedupeKey());
    response.setStatus(entity.getStatus());
    response.setReceivedAt(entity.getReceivedAt());
    response.setProcessingStartedAt(processingStartedAt);
    if (STATUS_PROCESSING.equals(entity.getStatus()) && processingStartedAt != null) {
      response.setProcessingDurationMs(Math.max(0L, System.currentTimeMillis() - processingStartedAt));
    }
    response.setSyncTargets(inputContentAutoTagger.parseTags(syncTargets));
    response.setSyncStatus(resolveSyncStatus(syncTargets, entity.getSyncStatus()));
    response.setSyncedAt(entity.getSyncedAt());
    response.setExternalReferencesJson(entity.getExternalReferencesJson());
    response.setCategory(autoTagResult.category());
    response.setTags(autoTagResult.tags());
    response.setProcessingAttemptCount(entity.getProcessingAttemptCount());
    return response;
  }

  private JsonResult<?> createMessageForUsername(InputMessageCreateParam inputMessageCreateParam,
      String username) {
    if (inputMessageCreateParam == null || inputMessageCreateParam.getDedupeKey() == null
        || inputMessageCreateParam.getDedupeKey().isBlank()) {
      log.warn(
          "create input message failed, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}, reason=invalid_payload, errorMessage={}",
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getDedupeKey(),
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getSessionId(),
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getContentType(),
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getSyncTargets(),
          inputMessageCreateParam == null ? "request body is null" : "dedupeKey 不能为空");
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    var existing = inputMessageRepository.findFirstByDedupeKey(inputMessageCreateParam.getDedupeKey());
    if (existing.isPresent()) {
      if (!username.equals(existing.get().getCreatedBy())) {
        log.warn(
            "input message create rejected, dedupeKey={}, sessionId={}, contentType={}, requestedBy={}, reason=dedupe_key_owned_by_other_user, existingMessageId={}",
            inputMessageCreateParam.getDedupeKey(), inputMessageCreateParam.getSessionId(),
            inputMessageCreateParam.getContentType(), username, existing.get().getId());
        return ResultTool.fail(ResultCode.NO_PERMISSION);
      }
      log.info("input message dedupe hit, dedupeKey={}, user={}, messageId={}",
          inputMessageCreateParam.getDedupeKey(), username, existing.get().getId());
      return ResultTool.success(existing.get());
    }

    String payloadValidationError = validateCreateMessagePayload(inputMessageCreateParam);
    if (payloadValidationError != null) {
      log.warn(
          "create input message failed, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}, reason=invalid_payload, errorMessage={}",
          inputMessageCreateParam.getDedupeKey(), inputMessageCreateParam.getSessionId(),
          inputMessageCreateParam.getContentType(), inputMessageCreateParam.getSyncTargets(),
          payloadValidationError);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    String normalizedSyncTargets;
    try {
      normalizedSyncTargets = normalizeSyncTargets(inputMessageCreateParam.getSyncTargets());
    } catch (IllegalArgumentException ex) {
      log.warn(
          "create input message failed, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}, reason=unsupported_sync_target, errorMessage={}",
          inputMessageCreateParam.getDedupeKey(), inputMessageCreateParam.getSessionId(),
          inputMessageCreateParam.getContentType(), inputMessageCreateParam.getSyncTargets(),
          resolveExceptionMessage(ex));
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    InputMessageEntity entity = new InputMessageEntity();
    entity.setSourceType(inputMessageCreateParam.getSourceType());
    entity.setSourceAccountId(inputMessageCreateParam.getSourceAccountId());
    entity.setSessionId(inputMessageCreateParam.getSessionId());
    entity.setSenderId(inputMessageCreateParam.getSenderId());
    entity.setContentType(inputMessageCreateParam.getContentType());
    entity.setRawContent(inputMessageCreateParam.getRawContent());
    entity.setNormalizedContent(inputMessageCreateParam.getNormalizedContent() == null
        || inputMessageCreateParam.getNormalizedContent().isBlank()
            ? inputMessageCreateParam.getRawContent()
            : inputMessageCreateParam.getNormalizedContent());
    entity.setAttachmentsJson(JSON.toJSONString(inputMessageCreateParam.getAttachments()));
    entity.setDedupeKey(inputMessageCreateParam.getDedupeKey());
    String initialStatus = inputMessageCreateParam.getStatus() == null || inputMessageCreateParam.getStatus().isBlank()
        ? STATUS_RECEIVED
        : inputMessageCreateParam.getStatus();
    entity.setReceivedAt(inputMessageCreateParam.getReceivedAt() == null
        ? System.currentTimeMillis()
        : inputMessageCreateParam.getReceivedAt());
    applyStatus(entity, initialStatus, entity.getReceivedAt());
    entity.setCreatedBy(username);
    entity.setSyncTargets(normalizedSyncTargets);
    entity.setSyncStatus(resolveSyncStatus(normalizedSyncTargets, null));
    entity.setSyncedAt(null);
    entity.setExternalReferencesJson(null);

    InputMessageEntity savedEntity = inputMessageRepository.save(entity);
    log.info("input message created, id={}, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}, user={}",
        savedEntity.getId(), savedEntity.getDedupeKey(), savedEntity.getSessionId(), savedEntity.getContentType(),
        savedEntity.getSyncTargets(), username);
    return ResultTool.success(savedEntity);
  }

  private JsonResult<?> processMessageForUsername(long id, String username) {
    var entityOptional = inputMessageRepository.findById(id);
    if (entityOptional.isEmpty() || !username.equals(entityOptional.get().getCreatedBy())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }

    InputMessageEntity entity = entityOptional.get();
    if (!STATUS_RECEIVED.equals(entity.getStatus()) && !STATUS_FAILED.equals(entity.getStatus())) {
      return ResultTool.success(entity);
    }

    String normalizedContent = entity.getNormalizedContent();
    if (normalizedContent == null || normalizedContent.isBlank()) {
      normalizedContent = entity.getRawContent();
    }
    if (normalizedContent == null || normalizedContent.isBlank()) {
      log.warn(
          "input message processing rejected, messageId={}, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}, reason=blank_content",
          entity.getId(), entity.getDedupeKey(), entity.getSessionId(), entity.getContentType(),
          entity.getSyncTargets());
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    long processingStartedAt = System.currentTimeMillis();
    applyStatus(entity, STATUS_PROCESSING, processingStartedAt);
    InputMessageEntity savedEntity = inputMessageRepository.save(entity);
    String sessionId = entity.getSessionId();
    String dedupeKey = entity.getDedupeKey();
    String createdBy = entity.getCreatedBy();
    String contentType = entity.getContentType();
    String rawContent = entity.getRawContent();
    String syncTargets = entity.getSyncTargets();
    String attemptToken = savedEntity.getProcessingAttemptToken();

    log.info(
        "input message processing queued, id={}, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}, processingStartedAt={}, attemptToken={}, user={}",
        id, dedupeKey, sessionId, contentType, syncTargets, processingStartedAt, attemptToken, username);

    taskExecutor.execute(() -> {
      log.info("input message processing started, id={}, dedupeKey={}, sessionId={}, contentType={}, processingStartedAt={}, attemptToken={}",
          id, dedupeKey, sessionId, contentType, processingStartedAt, attemptToken);
      try {
        String contentToIngest;
        if (isUrlContentType(contentType)) {
          contentToIngest = resolveUrlContentForIngest(entity);
        } else if (CONTENT_TYPE_IMAGE.equalsIgnoreCase(contentType)) {
          contentToIngest = resolveImageContentForIngest(entity);
        } else if (CONTENT_TYPE_VOICE.equalsIgnoreCase(contentType)) {
          contentToIngest = resolveVoiceContentForIngest(entity);
        } else {
          contentToIngest = entity.getNormalizedContent();
        }
        if (contentToIngest == null || contentToIngest.isBlank()) {
          contentToIngest = rawContent;
        }
        if (contentToIngest == null || contentToIngest.isBlank()) {
          throw new IllegalArgumentException("Text content is blank");
        }

        InputContentAutoTagger.AutoTagResult autoTagResult =
            resolveAutoTagResult(contentType, rawContent, contentToIngest);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("dedupeKey", dedupeKey);
        metadata.put("createdBy", createdBy);
        metadata.put("contentType", contentType == null ? CONTENT_TYPE_TEXT : contentType);
        metadata.put("contentCategory", autoTagResult.category());
        metadata.put("contentTags", String.join(",", autoTagResult.tags()));
        if (entity.getSyncTargets() != null && !entity.getSyncTargets().isBlank()) {
          metadata.put("syncTargets", entity.getSyncTargets());
        }
        metadata.put("syncStatus", resolveSyncStatus(entity.getSyncTargets(), entity.getSyncStatus()));
        if (isUrlContentType(contentType) && rawContent != null && !rawContent.isBlank()) {
          metadata.put("sourceUrl", rawContent);
        }
        RagUtility.ingestTextToChroma(contentToIngest, metadata, embeddingModel,
            knowledgeChatEmbeddingStore);
        if (feishuSyncService != null && shouldSyncToTarget(entity.getSyncTargets(), SYNC_TARGET_FEISHU)) {
          try {
            log.info("input message Feishu sync starting, id={}, dedupeKey={}, sessionId={}", id, dedupeKey,
                sessionId);
            FeishuSyncService.FeishuSyncResult feishuSyncResult =
                feishuSyncService.syncMessage(entity, contentToIngest);
            updateTargetSyncState(id, SYNC_TARGET_FEISHU, SYNC_STATUS_SYNCED, feishuSyncResult.syncedAt(),
                feishuSyncResult.reference());
            log.info("input message Feishu sync succeeded, id={}, dedupeKey={}, mode={}, documentId={}", id,
                dedupeKey, feishuSyncResult.reference().get("mode"),
                feishuSyncResult.reference().get("documentId"));
          } catch (RuntimeException syncException) {
            log.error(
                "input message feishu sync failed, id={}, dedupeKey={}, sessionId={}, target={}, syncTargets={}, errorType={}, errorMessage={}",
                id, dedupeKey, sessionId, SYNC_TARGET_FEISHU, syncTargets, syncException.getClass().getSimpleName(),
                resolveExceptionMessage(syncException), syncException);
            updateTargetSyncState(id, SYNC_TARGET_FEISHU, SYNC_STATUS_FAILED, null,
                buildTargetSyncFailureReference(syncException));
          }
        }
        updateStatusIfAttemptMatches(id, STATUS_INGESTED, attemptToken);
        log.info("input message processing completed, id={}, dedupeKey={}, sessionId={}, contentType={}, finalStatus={}",
            id, dedupeKey, sessionId, contentType, STATUS_INGESTED);
      } catch (Exception e) {
        log.error(
            "input message processing failed, id={}, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}, finalStatus={}",
            id, dedupeKey, sessionId, contentType, syncTargets, STATUS_FAILED, e);
        updateStatusIfAttemptMatches(id, STATUS_FAILED, attemptToken);
      }
    });

    return ResultTool.success(savedEntity);
  }

  private String buildWechatSessionId(String appid, String openid) {
    return SOURCE_TYPE_WECHAT + ":" + appid + ":" + openid;
  }

  private String buildWechatDedupeKey(String appid, String openid, String externalMessageId) {
    if (externalMessageId != null && !externalMessageId.isBlank()) {
      return SOURCE_TYPE_WECHAT + ":" + appid + ":" + openid + ":" + externalMessageId;
    }
    return SOURCE_TYPE_WECHAT + ":" + appid + ":" + openid + ":" + UUID.randomUUID();
  }

  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> bridgeWechatUrlMessage(String appid, String openid, String username,
      String url, String title, String description, String externalMessageId) {
    if (appid == null || appid.isBlank() || openid == null || openid.isBlank() || username == null
        || username.isBlank() || url == null || url.isBlank()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    InputMessageCreateParam inputMessageCreateParam = new InputMessageCreateParam();
    inputMessageCreateParam.setSourceType(SOURCE_TYPE_WECHAT);
    inputMessageCreateParam.setSourceAccountId(appid);
    inputMessageCreateParam.setSessionId(buildWechatSessionId(appid, openid));
    inputMessageCreateParam.setSenderId(openid);
    inputMessageCreateParam.setContentType(CONTENT_TYPE_URL);
    inputMessageCreateParam.setRawContent(url);
    inputMessageCreateParam.setNormalizedContent(UrlContentNormalizer.buildLinkSummary(title, description, url));
    inputMessageCreateParam.setDedupeKey(buildWechatDedupeKey(appid, openid, externalMessageId));

    JsonResult<?> createResult = createMessageForUsername(inputMessageCreateParam, username);
    if (!createResult.getSuccess() || !(createResult.getData() instanceof InputMessageEntity entity)) {
      return createResult;
    }

    JsonResult<?> processResult = processMessageForUsername(entity.getId(), username);
    if (!processResult.getSuccess()) {
      return processResult;
    }
    return ResultTool.success(entity);
  }

  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> bridgeWechatTextMessage(String appid, String openid, String username,
      String content, String externalMessageId) {
    if (appid == null || appid.isBlank() || openid == null || openid.isBlank() || username == null
        || username.isBlank() || content == null || content.isBlank()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    InputMessageCreateParam inputMessageCreateParam = new InputMessageCreateParam();
    inputMessageCreateParam.setSourceType(SOURCE_TYPE_WECHAT);
    inputMessageCreateParam.setSourceAccountId(appid);
    inputMessageCreateParam.setSessionId(buildWechatSessionId(appid, openid));
    inputMessageCreateParam.setSenderId(openid);
    inputMessageCreateParam.setContentType(CONTENT_TYPE_TEXT);
    inputMessageCreateParam.setRawContent(content);
    inputMessageCreateParam.setNormalizedContent(content);
    inputMessageCreateParam.setDedupeKey(buildWechatDedupeKey(appid, openid, externalMessageId));

    JsonResult<?> createResult = createMessageForUsername(inputMessageCreateParam, username);
    if (!createResult.getSuccess() || !(createResult.getData() instanceof InputMessageEntity entity)) {
      return createResult;
    }

    JsonResult<?> processResult = processMessageForUsername(entity.getId(), username);
    if (!processResult.getSuccess()) {
      return processResult;
    }
    return ResultTool.success(entity);
  }

  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> bridgeWechatImageMessage(String appid, String openid, String username,
      String imageUrl, String externalMessageId) {
    if (appid == null || appid.isBlank() || openid == null || openid.isBlank() || username == null
        || username.isBlank() || imageUrl == null || imageUrl.isBlank()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    InputAttachmentParam attachment = new InputAttachmentParam();
    attachment.setName(WECHAT_IMAGE_ATTACHMENT_NAME);
    attachment.setUrl(imageUrl);
    attachment.setContentType(WECHAT_IMAGE_MIME_TYPE);

    InputMessageCreateParam inputMessageCreateParam = new InputMessageCreateParam();
    inputMessageCreateParam.setSourceType(SOURCE_TYPE_WECHAT);
    inputMessageCreateParam.setSourceAccountId(appid);
    inputMessageCreateParam.setSessionId(buildWechatSessionId(appid, openid));
    inputMessageCreateParam.setSenderId(openid);
    inputMessageCreateParam.setContentType(CONTENT_TYPE_IMAGE);
    inputMessageCreateParam.setRawContent(imageUrl);
    inputMessageCreateParam.setNormalizedContent(imageUrl);
    inputMessageCreateParam.setAttachments(List.of(attachment));
    inputMessageCreateParam.setDedupeKey(buildWechatDedupeKey(appid, openid, externalMessageId));

    JsonResult<?> createResult = createMessageForUsername(inputMessageCreateParam, username);
    if (!createResult.getSuccess() || !(createResult.getData() instanceof InputMessageEntity entity)) {
      return createResult;
    }

    JsonResult<?> processResult = processMessageForUsername(entity.getId(), username);
    if (!processResult.getSuccess()) {
      return processResult;
    }
    return ResultTool.success(entity);
  }

  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> bridgeWechatVoiceMessage(String appid, String openid, String username,
      String audioUrl, String transcribedText, String externalMessageId) {
    if (appid == null || appid.isBlank() || openid == null || openid.isBlank() || username == null
        || username.isBlank() || transcribedText == null || transcribedText.isBlank()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    InputAttachmentParam attachment = new InputAttachmentParam();
    attachment.setName(WECHAT_VOICE_ATTACHMENT_NAME);
    attachment.setUrl(audioUrl);
    attachment.setContentType(WECHAT_VOICE_MIME_TYPE);

    InputMessageCreateParam inputMessageCreateParam = new InputMessageCreateParam();
    inputMessageCreateParam.setSourceType(SOURCE_TYPE_WECHAT);
    inputMessageCreateParam.setSourceAccountId(appid);
    inputMessageCreateParam.setSessionId(buildWechatSessionId(appid, openid));
    inputMessageCreateParam.setSenderId(openid);
    inputMessageCreateParam.setContentType(CONTENT_TYPE_VOICE);
    inputMessageCreateParam.setRawContent(audioUrl == null || audioUrl.isBlank() ? transcribedText : audioUrl);
    inputMessageCreateParam.setNormalizedContent(transcribedText);
    if (audioUrl != null && !audioUrl.isBlank()) {
      inputMessageCreateParam.setAttachments(List.of(attachment));
    }
    inputMessageCreateParam.setDedupeKey(buildWechatDedupeKey(appid, openid, externalMessageId));

    JsonResult<?> createResult = createMessageForUsername(inputMessageCreateParam, username);
    if (!createResult.getSuccess() || !(createResult.getData() instanceof InputMessageEntity entity)) {
      return createResult;
    }

    JsonResult<?> processResult = processMessageForUsername(entity.getId(), username);
    if (!processResult.getSuccess()) {
      return processResult;
    }
    return ResultTool.success(entity);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> createMessage(InputMessageCreateParam inputMessageCreateParam, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("create input message failed to parse token, dedupeKey={}, sessionId={}, contentType={}, syncTargets={}",
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getDedupeKey(),
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getSessionId(),
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getContentType(),
          inputMessageCreateParam == null ? null : inputMessageCreateParam.getSyncTargets(), e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return createMessageForUsername(inputMessageCreateParam, username);
  }

  @Override
  public JsonResult<?> getMessageByDedupeKey(String dedupeKey, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("get input message by dedupe key failed to parse token, dedupeKey={}", dedupeKey, e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var entity = inputMessageRepository.findFirstByDedupeKeyAndCreatedBy(dedupeKey, username);
    return entity.<JsonResult<?>>map(found -> ResultTool.success(toResponse(found)))
        .orElseGet(() -> ResultTool.fail(ResultCode.NO_PERMISSION));
  }

  @Override
  public JsonResult<?> getMessagesBySessionId(String sessionId, String token) {
    return getMessagesBySessionId(sessionId, null, null, token);
  }

  @Override
  public JsonResult<?> getMessagesBySessionId(String sessionId, Integer page, Integer size, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("get input messages by session failed to parse token, sessionId={}, page={}, size={}",
          sessionId, page, size, e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    return getMessagesBySessionIdForUsername(sessionId, page, size, username);
  }

  @Override
  public JsonResult<?> getStaleProcessingMessages(String sessionId, Integer olderThanMinutes, Integer limit,
      String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("get stale processing messages failed to parse token, sessionId={}, olderThanMinutes={}, limit={}",
          sessionId, olderThanMinutes, limit, e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    return getStaleProcessingMessagesForUsername(sessionId, olderThanMinutes, limit, username);
  }

  private JsonResult<?> getMessagesBySessionIdForUsername(String sessionId, Integer page, Integer size,
      String username) {
    if (page == null && size == null) {
      var entities = inputMessageRepository.findAllBySessionIdAndCreatedByOrderByReceivedAtDesc(sessionId,
          username);
      if (entities.isEmpty()) {
        return ResultTool.fail(ResultCode.NO_PERMISSION);
      }
      return ResultTool.success(entities.stream().map(this::toResponse)
          .collect(Collectors.toList()));
    }

    int requestedPage = page == null ? DEFAULT_SESSION_QUERY_PAGE : page;
    int requestedSize = size == null ? DEFAULT_SESSION_QUERY_SIZE : size;
    if (requestedPage < 0 || requestedSize < 1 || requestedSize > MAX_SESSION_QUERY_SIZE) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    Page<InputMessageEntity> entitiesPage = inputMessageRepository.findAllBySessionIdAndCreatedBy(sessionId,
        username, PageRequest.of(requestedPage, requestedSize, Sort.by(Sort.Direction.DESC, "receivedAt")));
    if (entitiesPage.isEmpty() && entitiesPage.getTotalElements() == 0) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }
    log.info("input messages queried by session, sessionId={}, user={}, page={}, size={}, returned={}",
        sessionId, username, requestedPage, requestedSize, entitiesPage.getNumberOfElements());
    return ResultTool.success(entitiesPage.getContent().stream().map(this::toResponse)
        .collect(Collectors.toList()));
  }

  private JsonResult<?> getStaleProcessingMessagesForUsername(String sessionId, Integer olderThanMinutes,
      Integer limit, String username) {
    int requestedOlderThanMinutes =
        olderThanMinutes == null ? DEFAULT_STALE_PROCESSING_MINUTES : olderThanMinutes;
    int requestedLimit = limit == null ? DEFAULT_STALE_PROCESSING_LIMIT : limit;
    if (requestedOlderThanMinutes < 1 || requestedLimit < 1 || requestedLimit > MAX_STALE_PROCESSING_LIMIT) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    long threshold = System.currentTimeMillis() - requestedOlderThanMinutes * 60_000L;
    PageRequest pageRequest = PageRequest.of(0, requestedLimit);
    Page<InputMessageEntity> staleEntitiesPage;
    if (sessionId == null || sessionId.isBlank()) {
      staleEntitiesPage = inputMessageRepository.findAllByCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
          username, STATUS_PROCESSING, threshold, pageRequest);
    } else {
      staleEntitiesPage =
          inputMessageRepository.findAllBySessionIdAndCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
              sessionId, username, STATUS_PROCESSING, threshold, pageRequest);
    }
    log.info(
        "stale processing messages queried, sessionId={}, user={}, olderThanMinutes={}, limit={}, returned={}",
        sessionId, username, requestedOlderThanMinutes, requestedLimit, staleEntitiesPage.getNumberOfElements());
    return ResultTool.success(staleEntitiesPage.getContent().stream().map(this::toResponse)
        .collect(Collectors.toList()));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> processMessage(long id, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("process input message failed to parse token, messageId={}", id, e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return processMessageForUsername(id, username);
  }

  private JsonResult<?> retryMessageForUsername(long id, String username) {
    var entityOptional = inputMessageRepository.findById(id);
    if (entityOptional.isEmpty() || !username.equals(entityOptional.get().getCreatedBy())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }

    InputMessageEntity entity = entityOptional.get();
    if (!STATUS_FAILED.equals(entity.getStatus())) {
      log.warn(
          "input message retry rejected, messageId={}, dedupeKey={}, sessionId={}, currentStatus={}, syncStatus={}, requestedBy={}",
          entity.getId(), entity.getDedupeKey(), entity.getSessionId(), entity.getStatus(),
          resolveSyncStatus(entity.getSyncTargets(), entity.getSyncStatus()), username);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    log.info("input message retry queued, messageId={}, dedupeKey={}, sessionId={}, previousStatus={}, syncStatus={}, user={}",
        entity.getId(), entity.getDedupeKey(), entity.getSessionId(), entity.getStatus(),
        resolveSyncStatus(entity.getSyncTargets(), entity.getSyncStatus()), username);
    JsonResult<?> processResult = processMessageForUsername(id, username);
    if (!processResult.getSuccess()) {
      return processResult;
    }
    return ResultTool.success(toResponse(entity));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> retryMessage(long id, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("retry input message failed to parse token, messageId={}", id, e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return retryMessageForUsername(id, username);
  }

  @Override
  public JsonResult<?> recallMessages(InputMessageRecallParam inputMessageRecallParam, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("recall input messages failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    try {
      EmbeddingSearchResult<TextSegment> searchResult = RagUtility.searchByCreatedByAndSessionId(
          knowledgeChatEmbeddingStore, embeddingModel, inputMessageRecallParam.getQuery(), username,
          inputMessageRecallParam.getSessionId(), 5, 0.6);
        List<InputMessageRecallItemResponse> result = searchResult.matches().stream()
          .map(match -> {
            String contentType = match.embedded().metadata().getString("contentType");
            String contentCategory = match.embedded().metadata().getString("contentCategory");
            List<String> tags = inputContentAutoTagger.parseTags(match.embedded().metadata().getString("contentTags"));
            if (contentCategory == null || contentCategory.isBlank()) {
              contentCategory = inputContentAutoTagger.analyze(contentType, null, match.embedded().text()).category();
            }
            if (tags.isEmpty()) {
              tags = inputContentAutoTagger.analyze(contentType, null, match.embedded().text()).tags();
            }
            InputMessageRecallItemResponse item = new InputMessageRecallItemResponse();
            item.setText(match.embedded().text());
            item.setScore(match.score());
            item.setSessionId(match.embedded().metadata().getString("sessionId"));
            item.setDedupeKey(match.embedded().metadata().getString("dedupeKey"));
            item.setCategory(contentCategory);
            item.setTags(tags);
            return item;
          })
          .collect(Collectors.toList());
      return ResultTool.success(result);
    } catch (Exception e) {
      log.error("recall input messages failed, user={}", username, e);
      return ResultTool.fail(ResultCode.COMMON_FAIL);
    }
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> promoteMessageToLongMemory(long id, InputMessagePromoteParam inputMessagePromoteParam,
      String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("promote input message failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var entityOptional = inputMessageRepository.findById(id);
    if (entityOptional.isEmpty() || !username.equals(entityOptional.get().getCreatedBy())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }

    InputMessageEntity entity = entityOptional.get();
    if (!STATUS_INGESTED.equals(entity.getStatus())) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    String memoryValue = resolvePromotedMemoryValue(entity, inputMessagePromoteParam);
    if (memoryValue == null || memoryValue.isBlank() || memoryValue.length() > 1024) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    AgentLongMemory agentLongMemory = new AgentLongMemory();
    agentLongMemory.setProductId(inputMessagePromoteParam.getProductId());
    agentLongMemory.setMemoryKey(inputMessagePromoteParam.getMemoryKey());
    agentLongMemory.setDescription(inputMessagePromoteParam.getDescription());
    agentLongMemory.setMemoryValue(memoryValue);
    return agentLongMemoryService.postLongMemory(agentLongMemory);
  }

  @Override
  public JsonResult<?> listMessages(String sourceType, String status, String contentType,
      String keyword, Boolean archived, Long startTime, Long endTime,
      Integer page, Integer size, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("list input messages failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    int pageNum = (page != null && page >= 0) ? page : 0;
    int pageSize = (size != null && size > 0 && size <= 100) ? size : 20;
    PageRequest pageable = PageRequest.of(pageNum, pageSize);

    // 标准化空字符串参数为 null
    String srcType = (sourceType != null && !sourceType.isBlank()) ? sourceType : null;
    String sts = (status != null && !status.isBlank()) ? status : null;
    String cntType = (contentType != null && !contentType.isBlank()) ? contentType : null;
    String kw = (keyword != null && !keyword.isBlank()) ? keyword : null;
    boolean archivedOnly = Boolean.TRUE.equals(archived);

    Page<InputMessageEntity> result = inputMessageRepository.searchMessages(
        username, srcType, sts, cntType, kw, archivedOnly, startTime, endTime, pageable);

    JSONObject data = new JSONObject();
    data.put("content", result.getContent());
    data.put("totalElements", result.getTotalElements());
    data.put("totalPages", result.getTotalPages());
    data.put("page", result.getNumber());
    data.put("size", result.getSize());
    return ResultTool.success(data);
  }

  @Override
  public JsonResult<?> getMessageStats(String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("get message stats failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    JSONObject data = new JSONObject();

    // 按状态分组
    List<Object[]> statusCounts = inputMessageRepository.countByStatusGrouped(username);
    JSONObject byStatus = new JSONObject();
    long totalCount = 0;
    for (Object[] row : statusCounts) {
      String statusKey = (String) row[0];
      Long count = (Long) row[1];
      byStatus.put(statusKey != null ? statusKey : "unknown", count);
      totalCount += count;
    }
    data.put("byStatus", byStatus);

    // 按来源类型分组
    List<Object[]> sourceCounts = inputMessageRepository.countBySourceTypeGrouped(username);
    JSONObject bySource = new JSONObject();
    for (Object[] row : sourceCounts) {
      String srcKey = (String) row[0];
      Long count = (Long) row[1];
      bySource.put(srcKey != null ? srcKey : "unknown", count);
    }
    data.put("bySource", bySource);

    data.put("total", totalCount);
    return ResultTool.success(data);
  }

  @Override
  public JsonResult<?> getMessageById(long id, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("get message by id failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var optMsg = inputMessageRepository.findById(id);
    if (optMsg.isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    InputMessageEntity msg = optMsg.get();
    if (!username.equals(msg.getCreatedBy())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }
    return ResultTool.success(msg);
  }

  @Override
  @Transactional
  public JsonResult<?> deleteMessage(long id, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("delete message failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var optMsg = inputMessageRepository.findById(id);
    if (optMsg.isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    InputMessageEntity msg = optMsg.get();
    if (!username.equals(msg.getCreatedBy())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }

    inputMessageRepository.deleteById(id);
    log.info("Message deleted: id={}, createdBy={}", id, username);
    return ResultTool.success();
  }

  @Override
  public JsonResult<?> getSyncStats(String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("get sync stats failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    JSONObject data = new JSONObject();

    // 按同步状态分组
    List<Object[]> syncStatusCounts = inputMessageRepository.countBySyncStatusGrouped(username);
    JSONObject bySyncStatus = new JSONObject();
    long totalSyncable = 0;
    for (Object[] row : syncStatusCounts) {
      String statusKey = (String) row[0];
      Long count = (Long) row[1];
      bySyncStatus.put(statusKey != null ? statusKey : "unknown", count);
      totalSyncable += count;
    }
    data.put("bySyncStatus", bySyncStatus);
    data.put("totalSyncable", totalSyncable);

    return ResultTool.success(data);
  }

  @Override
  public JsonResult<?> listSyncMessages(String syncStatus, Integer page, Integer size, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("list sync messages failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    int pageNum = (page != null && page >= 0) ? page : 0;
    int pageSize = (size != null && size > 0 && size <= 100) ? size : 20;
    String sts = (syncStatus != null && !syncStatus.isBlank()) ? syncStatus.trim() : null;

    Page<InputMessageEntity> result = inputMessageRepository.findSyncMessages(
        username, sts, PageRequest.of(pageNum, pageSize));

    JSONObject data = new JSONObject();
    data.put("content", result.getContent());
    data.put("totalElements", result.getTotalElements());
    data.put("totalPages", result.getTotalPages());
    data.put("page", result.getNumber());
    data.put("size", result.getSize());
    return ResultTool.success(data);
  }

  @Override
  @Transactional
  public JsonResult<?> batchProcessMessages(List<Long> ids, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("batch process messages failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    if (ids == null || ids.isEmpty() || ids.size() > 50) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    int successCount = 0;
    int skipCount = 0;
    for (Long id : ids) {
      var opt = inputMessageRepository.findById(id);
      if (opt.isEmpty() || !username.equals(opt.get().getCreatedBy())) {
        skipCount++;
        continue;
      }
      InputMessageEntity entity = opt.get();
      if (!STATUS_RECEIVED.equals(entity.getStatus()) && !STATUS_FAILED.equals(entity.getStatus())) {
        skipCount++;
        continue;
      }
      processMessageForUsername(id, username);
      successCount++;
    }

    JSONObject data = new JSONObject();
    data.put("processed", successCount);
    data.put("skipped", skipCount);
    data.put("total", ids.size());
    log.info("Batch process completed: user={}, processed={}, skipped={}, total={}", username, successCount, skipCount, ids.size());
    return ResultTool.success(data);
  }

  @Override
  @Transactional
  public JsonResult<?> batchDeleteMessages(List<Long> ids, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("batch delete messages failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    if (ids == null || ids.isEmpty() || ids.size() > 50) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    int deletedCount = 0;
    int skipCount = 0;
    for (Long id : ids) {
      var opt = inputMessageRepository.findById(id);
      if (opt.isEmpty() || !username.equals(opt.get().getCreatedBy())) {
        skipCount++;
        continue;
      }
      inputMessageRepository.deleteById(id);
      deletedCount++;
    }

    JSONObject data = new JSONObject();
    data.put("deleted", deletedCount);
    data.put("skipped", skipCount);
    data.put("total", ids.size());
    log.info("Batch delete completed: user={}, deleted={}, skipped={}, total={}", username, deletedCount, skipCount, ids.size());
    return ResultTool.success(data);
  }
}
