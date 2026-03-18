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

import top.rslly.iot.param.request.InputMessageCreateParam;
import top.rslly.iot.param.request.InputMessagePromoteParam;
import top.rslly.iot.param.request.InputMessageRecallParam;
import top.rslly.iot.utility.result.JsonResult;

import java.util.List;

public interface InputMessageService {
  JsonResult<?> createMessage(InputMessageCreateParam inputMessageCreateParam, String token);

  JsonResult<?> getMessageByDedupeKey(String dedupeKey, String token);

  JsonResult<?> getMessagesBySessionId(String sessionId, String token);

  JsonResult<?> getMessagesBySessionId(String sessionId, Integer page, Integer size, String token);

  JsonResult<?> getStaleProcessingMessages(String sessionId, Integer olderThanMinutes, Integer limit,
      String token);

  JsonResult<?> processMessage(long id, String token);

  JsonResult<?> retryMessage(long id, String token);

  /** 重新处理已入库消息（重跑 AI 分析、向量写入、知识图谱） */
  JsonResult<?> reprocessMessage(long id, String token);

  JsonResult<?> recallMessages(InputMessageRecallParam inputMessageRecallParam, String token);

  JsonResult<?> promoteMessageToLongMemory(long id, InputMessagePromoteParam inputMessagePromoteParam,
      String token);

  /** 收件箱：分页查询当前用户所有消息，支持按来源类型、状态、内容类型、关键词、日期范围、文档用途筛选 */
  JsonResult<?> listMessages(String sourceType, String status, String contentType,
      String keyword, Boolean archived, Long startTime, Long endTime,
      String documentPurpose, Integer page, Integer size, String token);

  /** 消息统计：按状态和来源类型分组统计 */
  JsonResult<?> getMessageStats(String token);

  /** 查询单条消息详情 */
  JsonResult<?> getMessageById(long id, String token);

  /** 更新消息文档用途（仅所有者可操作） */
  JsonResult<?> updateMessagePurpose(long id, String documentPurpose, String token);

  /** 删除消息（仅所有者可操作） */
  JsonResult<?> deleteMessage(long id, String token);

  /** 同步状态统计：按同步状态分组统计 */
  JsonResult<?> getSyncStats(String token);

  /** 同步消息列表：查询有同步目标的消息，支持按同步状态筛选 */
  JsonResult<?> listSyncMessages(String syncStatus, Integer page, Integer size, String token);

  /** 批量处理消息 */
  JsonResult<?> batchProcessMessages(List<Long> ids, String token);

  /** 批量删除消息 */
  JsonResult<?> batchDeleteMessages(List<Long> ids, String token);
}
