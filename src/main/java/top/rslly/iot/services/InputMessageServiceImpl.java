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
import top.rslly.iot.utility.input.InputContentAutoTagger;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.ai.rag.RagUtility;
import top.rslly.iot.utility.input.UrlContentNormalizer;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.annotation.Resource;
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
  private static final String SYNC_TARGET_GITHUB = "github";
  private static final String SYNC_STATUS_NOT_REQUESTED = "not_requested";
  private static final String SYNC_STATUS_PENDING = "pending";
  private static final String SYNC_STATUS_SYNCED = "synced";
  private static final String SYNC_STATUS_FAILED = "failed";
  private static final String WECHAT_IMAGE_ATTACHMENT_NAME = "image";
  private static final String WECHAT_VOICE_ATTACHMENT_NAME = "voice";
  private static final String WECHAT_IMAGE_MIME_TYPE = "image/jpeg";
  private static final String WECHAT_VOICE_MIME_TYPE = "audio/amr";
  private static final Set<String> SUPPORTED_SYNC_TARGETS = Set.of(SYNC_TARGET_FEISHU, SYNC_TARGET_GITHUB);

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
  private UrlContentNormalizer urlContentNormalizer;
  @Autowired
  private InputContentAutoTagger inputContentAutoTagger;
  @Autowired
  private FeishuSyncService feishuSyncService;

  private String resolveUsername(String token) {
    String tokenDeal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    return JwtTokenUtil.getUsername(tokenDeal);
  }

  private void updateStatus(long id, String status) {
    inputMessageRepository.findById(id).ifPresent(entity -> {
      entity.setStatus(status);
      inputMessageRepository.save(entity);
    });
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
    if (!normalizedResult.success()) {
      log.warn("url content normalization failed, messageId={}, reason={}", entity.getId(),
          normalizedResult.failureReason());
    }
    return normalizedContent;
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
    response.setSyncTargets(inputContentAutoTagger.parseTags(syncTargets));
    response.setSyncStatus(resolveSyncStatus(syncTargets, entity.getSyncStatus()));
    response.setSyncedAt(entity.getSyncedAt());
    response.setExternalReferencesJson(entity.getExternalReferencesJson());
    response.setCategory(autoTagResult.category());
    response.setTags(autoTagResult.tags());
    return response;
  }

  private JsonResult<?> createMessageForUsername(InputMessageCreateParam inputMessageCreateParam,
      String username) {
    var existing = inputMessageRepository.findFirstByDedupeKey(inputMessageCreateParam.getDedupeKey());
    if (existing.isPresent()) {
      if (!username.equals(existing.get().getCreatedBy())) {
        return ResultTool.fail(ResultCode.NO_PERMISSION);
      }
      return ResultTool.success(existing.get());
    }

    String normalizedSyncTargets;
    try {
      normalizedSyncTargets = normalizeSyncTargets(inputMessageCreateParam.getSyncTargets());
    } catch (IllegalArgumentException ex) {
      log.warn("create input message failed, unsupported sync target, dedupeKey={}",
          inputMessageCreateParam.getDedupeKey(), ex);
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
    entity.setStatus(inputMessageCreateParam.getStatus() == null || inputMessageCreateParam.getStatus().isBlank()
        ? STATUS_RECEIVED
        : inputMessageCreateParam.getStatus());
    entity.setReceivedAt(inputMessageCreateParam.getReceivedAt() == null
        ? System.currentTimeMillis()
        : inputMessageCreateParam.getReceivedAt());
    entity.setCreatedBy(username);
    entity.setSyncTargets(normalizedSyncTargets);
    entity.setSyncStatus(resolveSyncStatus(normalizedSyncTargets, null));
    entity.setSyncedAt(null);
    entity.setExternalReferencesJson(null);

    return ResultTool.success(inputMessageRepository.save(entity));
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
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    entity.setStatus(STATUS_PROCESSING);
    InputMessageEntity savedEntity = inputMessageRepository.save(entity);
    String sessionId = entity.getSessionId();
    String dedupeKey = entity.getDedupeKey();
    String createdBy = entity.getCreatedBy();
    String contentType = entity.getContentType();
    String rawContent = entity.getRawContent();

    taskExecutor.execute(() -> {
      try {
        String contentToIngest = isUrlContentType(contentType)
            ? resolveUrlContentForIngest(entity)
            : entity.getNormalizedContent();
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
        if (shouldSyncToTarget(entity.getSyncTargets(), SYNC_TARGET_FEISHU)) {
          try {
            FeishuSyncService.FeishuSyncResult feishuSyncResult =
                feishuSyncService.syncMessage(entity, contentToIngest);
            updateTargetSyncState(id, SYNC_TARGET_FEISHU, SYNC_STATUS_SYNCED, feishuSyncResult.syncedAt(),
                feishuSyncResult.reference());
          } catch (RuntimeException syncException) {
            log.error("input message feishu sync failed, id={}", id, syncException);
            updateTargetSyncState(id, SYNC_TARGET_FEISHU, SYNC_STATUS_FAILED, null,
                buildTargetSyncFailureReference(syncException));
          }
        }
        updateStatus(id, STATUS_INGESTED);
      } catch (Exception e) {
        log.error("input message ingest failed, id={}", id, e);
        updateStatus(id, STATUS_FAILED);
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
    return createMessageForUsername(inputMessageCreateParam, username);
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
      log.warn("create input message failed to parse token", e);
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
      log.warn("get input message by dedupe key failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var entity = inputMessageRepository.findFirstByDedupeKeyAndCreatedBy(dedupeKey, username);
    return entity.<JsonResult<?>>map(found -> ResultTool.success(toResponse(found)))
        .orElseGet(() -> ResultTool.fail(ResultCode.NO_PERMISSION));
  }

  @Override
  public JsonResult<?> getMessagesBySessionId(String sessionId, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("get input messages by session failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var entities = inputMessageRepository.findAllBySessionIdAndCreatedByOrderByReceivedAtDesc(sessionId,
        username);
    if (entities.isEmpty()) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }
    return ResultTool.success(entities.stream().map(this::toResponse)
        .collect(Collectors.toList()));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> processMessage(long id, String token) {
    String username;
    try {
      username = resolveUsername(token);
    } catch (Exception e) {
      log.warn("process input message failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return processMessageForUsername(id, username);
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
}
