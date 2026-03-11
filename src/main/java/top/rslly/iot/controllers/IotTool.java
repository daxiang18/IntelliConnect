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
import top.rslly.iot.param.request.*;
import top.rslly.iot.services.SafetyServiceImpl;
import top.rslly.iot.services.agent.OtaXiaozhiPassiveServiceImpl;
import top.rslly.iot.services.agent.OtaXiaozhiServiceImpl;
import top.rslly.iot.services.iot.AlarmEventServiceImpl;
import top.rslly.iot.services.iot.OtaPassiveServiceImpl;
import top.rslly.iot.services.iot.OtaServiceImpl;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;

/**
 * IoT domain controller for OTA, alarm events, and hardware-specific features.
 */
@RestController
@RequestMapping(value = "/api/v2")
@Validated
public class IotTool {
  @Autowired
  private OtaServiceImpl otaService;
  @Autowired
  private AlarmEventServiceImpl alarmEventService;
  @Autowired
  private SafetyServiceImpl safetyService;
  @Autowired
  private OtaPassiveServiceImpl otaPassiveService;
  @Autowired
  private OtaXiaozhiServiceImpl otaXiaozhiService;
  @Autowired
  private OtaXiaozhiPassiveServiceImpl otaXiaozhiPassiveService;

  @RequestMapping(value = "/otaUpload", method = RequestMethod.POST)
  public JsonResult<?> ota(@RequestParam("name") @NotBlank(message = "name 不能为空")
  @Size(min = 1, max = 255, message = "name 长度必须在 1 到 255 之间") String name,
      @RequestParam("productId") int productId,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile multipartFile,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaService.uploadBin(name, productId, multipartFile);
  }

  @RequestMapping(value = "/otaList", method = RequestMethod.GET)
  public JsonResult<?> otaList(@RequestHeader("Authorization") String header) {
    return otaService.otaList(header);
  }

  @RequestMapping(value = "/otaDelete", method = RequestMethod.DELETE)
  public JsonResult<?> otaDelete(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeOta(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaService.deleteBin(id);
  }

  @RequestMapping(value = "/otaEnable", method = RequestMethod.POST)
  public JsonResult<?> otaEnable(@RequestParam("name") @NotBlank(message = "name 不能为空")
  @Size(min = 1, max = 255, message = "name 长度必须在 1 到 255 之间") String name,
      @RequestParam("deviceName") @NotBlank(message = "deviceName 不能为空")
      @Size(min = 1, max = 255, message = "deviceName 长度必须在 1 到 255 之间") String deviceName,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeOta(header, name, deviceName))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaService.otaEnable(name, deviceName);
  }

  @RequestMapping(value = "/otaPassive", method = RequestMethod.GET)
  public JsonResult<?> otaPassiveList(@RequestHeader("Authorization") String header) {
    return otaPassiveService.otaPassiveList(header);
  }

  @RequestMapping(value = "/otaPassive", method = RequestMethod.POST)
  public JsonResult<?> otaPassivePost(@Valid @RequestBody OtaPassive otaPassive,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeDevice(header, otaPassive.getDeviceName()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaPassiveService.otaPassivePost(otaPassive);
  }

  @RequestMapping(value = "/otaPassiveEnable", method = RequestMethod.GET)
  public JsonResult<?> otaPassiveEnable(
      @RequestParam("deviceName") @NotBlank(message = "deviceName 不能为空")
      @Size(min = 1, max = 255, message = "deviceName 长度必须在 1 到 255 之间") String deviceName) {
    return otaPassiveService.otaPassiveEnable(deviceName);
  }

  @RequestMapping(value = "/otaPassive", method = RequestMethod.DELETE)
  public JsonResult<?> otaPassiveDelete(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeOtaPassive(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaPassiveService.otaPassiveDelete(id);
  }

  @Operation(summary = "获取告警事件列表")
  @RequestMapping(value = "/alarmEvent", method = RequestMethod.GET)
  public JsonResult<?> alarmEventList(@RequestHeader("Authorization") String header) {
    return alarmEventService.getAlarmEventList(header);
  }

  @Operation(summary = "新增告警事件")
  @RequestMapping(value = "/alarmEvent", method = RequestMethod.POST)
  public JsonResult<?> alarmEventPost(@Valid @RequestBody AlarmEvent alarmEvent,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, alarmEvent.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return alarmEventService.addAlarmEvent(alarmEvent);
  }

  @Operation(summary = "删除告警事件")
  @RequestMapping(value = "/alarmEvent", method = RequestMethod.DELETE)
  public JsonResult<?> alarmEventDelete(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeAlarmEvent(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return alarmEventService.deleteAlarmEvent(id);
  }

  @Operation(summary = "小智OTA管理列表")
  @RequestMapping(value = "/xiaozhi/otaManage", method = RequestMethod.GET)
  public JsonResult<?> xiaozhiOtaManageList(@RequestHeader("Authorization") String header) {
    return otaXiaozhiService.getOtaXiaozhiList(header);
  }

  @Operation(summary = "小智OTA新增")
  @RequestMapping(value = "/xiaozhi/otaManage", method = RequestMethod.POST)
  public JsonResult<?> xiaozhiOtaManagePost(@RequestParam("name") @NotBlank(message = "name 不能为空")
  @Size(min = 1, max = 255, message = "name 长度必须在 1 到 255 之间") String name,
      @RequestParam("productId") int productId,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile multipartFile,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaXiaozhiService.uploadOtaXiaozhi(name, productId, multipartFile);
  }

  @Operation(summary = "小智OTA删除")
  @RequestMapping(value = "/xiaozhi/otaManage", method = RequestMethod.DELETE)
  public JsonResult<?> xiaozhiOtaManageDelete(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeOtaXiaoZhi(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaXiaozhiService.deleteOtaXiaozhi(id);
  }

  @Operation(summary = "小智OTA启用")
  @RequestMapping(value = "/xiaozhi/otaManage", method = RequestMethod.PUT)
  public JsonResult<?> xiaozhiOtaManagePut(@RequestParam("id") int id,
      @RequestParam("deviceName") @NotBlank(message = "deviceName 不能为空") String deviceName,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeOtaXiaoZhi(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaXiaozhiService.enableOtaXiaozhi(id, deviceName);
  }

  @Operation(summary = "视觉理解")
  @RequestMapping(value = "/vision/explain", method = RequestMethod.POST)
  public JsonResult<?> visionExplain(
      @RequestParam("productId") int productId,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile multipartFile,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaXiaozhiService.visionExplain(productId, multipartFile, header);
  }

  @Operation(summary = "小智OTA被动升级列表")
  @RequestMapping(value = "/xiaozhi/otaPassive", method = RequestMethod.GET)
  public JsonResult<?> xiaozhiOtaPassiveList(@RequestHeader("Authorization") String header) {
    return otaXiaozhiPassiveService.getOtaXiaozhiPassiveList(header);
  }

  @Operation(summary = "小智OTA被动升级新增")
  @RequestMapping(value = "/xiaozhi/otaPassive", method = RequestMethod.POST)
  public JsonResult<?> xiaozhiOtaPassivePost(@Valid @RequestBody OtaXiaozhiPassive otaXiaozhiPassive,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, otaXiaozhiPassive.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaXiaozhiPassiveService.addOtaXiaozhiPassive(otaXiaozhiPassive);
  }

  @Operation(summary = "小智OTA被动升级删除")
  @RequestMapping(value = "/xiaozhi/otaPassive", method = RequestMethod.DELETE)
  public JsonResult<?> xiaozhiOtaPassiveDelete(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeXiaoZhiOtaPassive(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return otaXiaozhiPassiveService.deleteOtaXiaozhiPassive(id);
  }

  @RequestMapping(value = "/micro/{name}", method = RequestMethod.GET)
  public void micro(@PathVariable("name") @NotBlank(message = "name 不能为空")
  @Size(min = 1, max = 255, message = "name 长度必须 in 1 到 255 之间") String name,
      HttpServletResponse response)
      throws IOException {
    otaService.otaDevice(name, response);
  }
}
