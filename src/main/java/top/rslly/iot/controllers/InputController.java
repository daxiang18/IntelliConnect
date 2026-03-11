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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import top.rslly.iot.param.request.InputMessageCreateParam;
import top.rslly.iot.services.InputMessageServiceImpl;
import top.rslly.iot.utility.result.JsonResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/v2/input")
@Validated
public class InputController {
  @Autowired
  private InputMessageServiceImpl inputMessageService;

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
      @RequestHeader("Authorization") String header) {
    return inputMessageService.getMessagesBySessionId(sessionId, header);
  }
}
