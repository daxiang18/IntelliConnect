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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.rslly.iot.param.request.AiControl;
import top.rslly.iot.services.agent.AiServiceImpl;
import top.rslly.iot.utility.RuntimeMessage;
import top.rslly.iot.utility.SseEmitterUtil;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;

@RestController
@RequestMapping(value = "/api/v2")
@Validated
public class SharedTool {
  @Autowired
  private AiServiceImpl aiService;

  @Operation(summary = "用于获取平台运行环境信息", description = "单位为百分比")
  @RequestMapping(value = "/machineMessage", method = RequestMethod.GET)
  public JsonResult<?> machineMessage() {
    return ResultTool.success(RuntimeMessage.getMessage());
  }

  @Operation(summary = "使用大模型控制设备", description = "响应速度取决于大模型速度")
  @RequestMapping(value = "/aiControl", method = RequestMethod.POST)
  public JsonResult<?> aiControl(@Valid @RequestBody AiControl aiControl,
      @RequestHeader("Authorization") String header) {
    return aiService.getAiResponse(aiControl, header);
  }

  @Operation(summary = "使用大模型控制设备(语音)", description = "响应速度取决于大模型速度")
  @RequestMapping(value = "/aiControl/audio", method = RequestMethod.POST)
  public JsonResult<?> aiControl(
      @RequestParam("productId") int productId,
      @RequestParam("tts") boolean tts,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile multipartFile,
      @RequestHeader("Authorization") String header) {
    return aiService.getAiResponse(tts, false, productId, multipartFile, header);
  }

  @Operation(summary = "使用大模型控制设备(语音）", description = "响应速度取决于大模型速度")
  @RequestMapping(value = "/aiControl/audio/stream", method = RequestMethod.POST)
  public SseEmitter aiControlStream(
      @RequestParam("productId") int productId,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile multipartFile,
      @RequestHeader("Authorization") String header) {
    aiService.getAiResponse(true, true, productId, multipartFile, header);
    return SseEmitterUtil.connect("chatProduct" + productId);
  }

  @Operation(summary = "获取缓存音频文件(禁止调用)", description = "禁止调用")
  @RequestMapping(value = "/ai/tmp_voice/{name}", method = RequestMethod.GET)
  public void audioTmpGet(@PathVariable("name") @NotBlank(message = "name 不能为空")
  @Size(min = 1, max = 255, message = "name 长度必须在 1 到 255 之间") String name,
      HttpServletResponse response)
      throws IOException {
    aiService.audioTmpGet(name, response);
  }

  @RequestMapping(value = "/vision/explain", method = RequestMethod.POST)
  public String aiVision(@RequestParam("question") @NotBlank(message = "question 不能为空")
  @Size(min = 1, max = 2048, message = "question 长度必须在 1 到 2048 之间") String question,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile imageFile) {
    return aiService.getAiVisionIntent(question, imageFile);
  }
}
