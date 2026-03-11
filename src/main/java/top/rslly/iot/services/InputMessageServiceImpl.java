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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.rslly.iot.dao.InputMessageRepository;
import top.rslly.iot.models.InputMessageEntity;
import top.rslly.iot.param.request.InputMessageCreateParam;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.annotation.Resource;

@Service
@Slf4j
public class InputMessageServiceImpl implements InputMessageService {
  private static final String DEFAULT_STATUS = "received";

  @Resource
  private InputMessageRepository inputMessageRepository;

  private String resolveUsername(String token) {
    String tokenDeal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    return JwtTokenUtil.getUsername(tokenDeal);
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

    var existing = inputMessageRepository.findFirstByDedupeKey(inputMessageCreateParam.getDedupeKey());
    if (existing.isPresent()) {
      return ResultTool.success(existing.get());
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
        ? DEFAULT_STATUS
        : inputMessageCreateParam.getStatus());
    entity.setReceivedAt(inputMessageCreateParam.getReceivedAt() == null
        ? System.currentTimeMillis()
        : inputMessageCreateParam.getReceivedAt());
    entity.setCreatedBy(username);

    return ResultTool.success(inputMessageRepository.save(entity));
  }

  @Override
  public JsonResult<?> getMessageByDedupeKey(String dedupeKey, String token) {
    try {
      resolveUsername(token);
    } catch (Exception e) {
      log.warn("get input message by dedupe key failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var entity = inputMessageRepository.findFirstByDedupeKey(dedupeKey);
    return entity.<JsonResult<?>>map(ResultTool::success)
        .orElseGet(() -> ResultTool.fail(ResultCode.COMMON_FAIL));
  }

  @Override
  public JsonResult<?> getMessagesBySessionId(String sessionId, String token) {
    try {
      resolveUsername(token);
    } catch (Exception e) {
      log.warn("get input messages by session failed to parse token", e);
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var entities = inputMessageRepository.findAllBySessionIdOrderByReceivedAtDesc(sessionId);
    if (entities.isEmpty()) {
      return ResultTool.fail(ResultCode.COMMON_FAIL);
    }
    return ResultTool.success(entities);
  }
}
