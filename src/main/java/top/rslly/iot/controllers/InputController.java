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

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import top.rslly.iot.param.request.InputMessageCreateParam;
import top.rslly.iot.param.request.InputMessagePromoteParam;
import top.rslly.iot.param.request.InputMessageRecallParam;
import top.rslly.iot.services.InputMessageServiceImpl;
import top.rslly.iot.services.SafetyServiceImpl;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v2/input")
@Validated
public class InputController {
  @Autowired
  private InputMessageServiceImpl inputMessageService;
  @Autowired
  private SafetyServiceImpl safetyService;

  @Operation(summary = "接收标准化输入消息", description = "提供给微信侧车和其他输入源的统一消息接入骨架")
  @RequestMapping(value = "/messages", method = RequestMethod.POST)
  public JsonResult<?> createMessage(@Valid @RequestBody InputMessageCreateParam inputMessageCreateParam,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.createMessage(inputMessageCreateParam, header);
  }

  @Operation(summary = "按去重键查询标准化输入消息", description = "用于最小闭环验证已入库消息是否可查询")
  @RequestMapping(value = "/messages/by-dedupe", method = RequestMethod.GET)
  public JsonResult<?> getMessageByDedupeKey(@RequestParam("dedupeKey") String dedupeKey,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.getMessageByDedupeKey(dedupeKey, header);
  }

  @Operation(summary = "按会话查询标准化输入消息", description = "用于最小闭环验证同一会话消息列表")
  @RequestMapping(value = "/messages/by-session", method = RequestMethod.GET)
  public JsonResult<?> getMessagesBySessionId(@RequestParam("sessionId") String sessionId,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.getMessagesBySessionId(sessionId, page, size, header);
  }

  @Operation(summary = "查询疑似卡住的处理中输入消息",
      description = "仅返回当前调用者名下 status=processing 且 processingStartedAt 早于阈值的消息，帮助人工诊断")
  @RequestMapping(value = "/messages/stale-processing", method = RequestMethod.GET)
  public JsonResult<?> getStaleProcessingMessages(
      @RequestParam(value = "sessionId", required = false) String sessionId,
      @RequestParam(value = "olderThanMinutes", required = false) @Min(1) Integer olderThanMinutes,
      @RequestParam(value = "limit", required = false) @Min(1) Integer limit,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.getStaleProcessingMessages(sessionId, olderThanMinutes, limit, header);
  }

  @Operation(summary = "触发标准化输入消息处理", description = "异步将输入消息写入知识向量库并更新处理状态")
  @RequestMapping(value = "/messages/{id}/process", method = RequestMethod.POST)
  public JsonResult<?> processMessage(@PathVariable("id") @Min(1) long id,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.processMessage(id, header);
  }

  @Operation(summary = "重试失败的输入消息", description = "仅允许消息所有者对 status=failed 的消息重新排队处理")
  @RequestMapping(value = "/messages/{id}/retry", method = RequestMethod.POST)
  public JsonResult<?> retryMessage(@PathVariable("id") @Min(1) long id,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.retryMessage(id, header);
  }

  @Operation(summary = "重新处理已入库消息", description = "对 status=ingested/failed 的消息重跑 AI 分析、向量写入、知识图谱关联")
  @RequestMapping(value = "/messages/{id}/reprocess", method = RequestMethod.POST)
  public JsonResult<?> reprocessMessage(@PathVariable("id") @Min(1) long id,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.reprocessMessage(id, header);
  }

  @Operation(summary = "召回已处理的输入消息", description = "按当前认证用户及可选会话范围对已入向量库的消息做语义检索")
  @RequestMapping(value = "/messages/recall", method = RequestMethod.POST)
  public JsonResult<?> recallMessages(@Valid @RequestBody InputMessageRecallParam inputMessageRecallParam,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.recallMessages(inputMessageRecallParam, header);
  }

  @Operation(summary = "提升输入消息为长期记忆", description = "将已 ingest 的输入消息提升到指定产品的 AgentLongMemory")
  @RequestMapping(value = "/messages/{id}/promote-to-memory", method = RequestMethod.POST)
  public JsonResult<?> promoteMessageToLongMemory(@PathVariable("id") @Min(1) long id,
      @Valid @RequestBody InputMessagePromoteParam inputMessagePromoteParam,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, inputMessagePromoteParam.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return inputMessageService.promoteMessageToLongMemory(id, inputMessagePromoteParam, header);
  }

  @Operation(summary = "收件箱消息列表", description = "分页查询当前用户所有输入消息，支持按来源类型、状态、内容类型、关键词、日期范围、文档用途、内容分类、标签筛选")
  @RequestMapping(value = "/messages", method = RequestMethod.GET)
  public JsonResult<?> listMessages(
      @RequestParam(value = "sourceType", required = false) String sourceType,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "contentType", required = false) String contentType,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "archived", required = false) Boolean archived,
      @RequestParam(value = "startTime", required = false) Long startTime,
      @RequestParam(value = "endTime", required = false) Long endTime,
      @RequestParam(value = "documentPurpose", required = false) String documentPurpose,
      @RequestParam(value = "contentCategory", required = false) String contentCategory,
      @RequestParam(value = "contentTag", required = false) String contentTag,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.listMessages(sourceType, status, contentType, keyword, archived,
        startTime, endTime, documentPurpose, contentCategory, contentTag, page, size, header);
  }

  @Operation(summary = "消息统计", description = "按状态和来源类型分组统计当前用户消息数量")
  @RequestMapping(value = "/messages/stats", method = RequestMethod.GET)
  public JsonResult<?> getMessageStats(@RequestHeader("Authorization") String header) {
    return inputMessageService.getMessageStats(header);
  }

  @Operation(summary = "分类统计", description = "按内容分类和标签分组统计当前用户消息，用于分类侧边栏和标签云")
  @RequestMapping(value = "/messages/categories", method = RequestMethod.GET)
  public JsonResult<?> getCategoryStats(@RequestHeader("Authorization") String header) {
    return inputMessageService.getCategoryStats(header);
  }

  @Operation(summary = "查询单条消息详情", description = "按 ID 查询当前用户的单条输入消息完整信息")
  @RequestMapping(value = "/messages/{id}", method = RequestMethod.GET)
  public JsonResult<?> getMessageById(@PathVariable("id") @Min(1) long id,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.getMessageById(id, header);
  }

  @Operation(summary = "更新消息文档用途", description = "仅允许消息所有者修改文档用途分类")
  @RequestMapping(value = "/messages/{id}/purpose", method = RequestMethod.PUT)
  public JsonResult<?> updateMessagePurpose(@PathVariable("id") @Min(1) long id,
      @RequestParam("documentPurpose") String documentPurpose,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.updateMessagePurpose(id, documentPurpose, header);
  }

  @Operation(summary = "删除输入消息", description = "仅允许消息所有者删除自己的消息")
  @RequestMapping(value = "/messages/{id}", method = RequestMethod.DELETE)
  public JsonResult<?> deleteMessage(@PathVariable("id") @Min(1) long id,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.deleteMessage(id, header);
  }

  @Operation(summary = "同步状态统计", description = "按同步状态分组统计当前用户有同步目标的消息数量")
  @RequestMapping(value = "/messages/sync-stats", method = RequestMethod.GET)
  public JsonResult<?> getSyncStats(@RequestHeader("Authorization") String header) {
    return inputMessageService.getSyncStats(header);
  }

  @Operation(summary = "同步消息列表", description = "分页查询当前用户有同步目标的消息，支持按同步状态筛选")
  @RequestMapping(value = "/messages/sync-list", method = RequestMethod.GET)
  public JsonResult<?> listSyncMessages(
      @RequestParam(value = "syncStatus", required = false) String syncStatus,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.listSyncMessages(syncStatus, page, size, header);
  }

  @Operation(summary = "批量处理消息", description = "批量触发消息处理（最多50条），仅处理当前用户所有的 received/failed 状态消息")
  @RequestMapping(value = "/messages/batch-process", method = RequestMethod.POST)
  public JsonResult<?> batchProcessMessages(@RequestBody java.util.List<Long> ids,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.batchProcessMessages(ids, header);
  }

  @Operation(summary = "批量删除消息", description = "批量删除消息（最多50条），仅删除当前用户所有的消息")
  @RequestMapping(value = "/messages/batch-delete", method = RequestMethod.POST)
  public JsonResult<?> batchDeleteMessages(@RequestBody java.util.List<Long> ids,
      @RequestHeader("Authorization") String header) {
    return inputMessageService.batchDeleteMessages(ids, header);
  }
}
