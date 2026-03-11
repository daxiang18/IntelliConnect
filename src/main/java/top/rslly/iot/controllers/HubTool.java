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
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.rslly.iot.param.request.*;
import top.rslly.iot.services.SafetyServiceImpl;
import top.rslly.iot.services.iot.HardWareServiceImpl;
import top.rslly.iot.services.storage.DataServiceImpl;
import top.rslly.iot.services.storage.EventStorageServiceImpl;
import top.rslly.iot.services.thingsModel.ProductDeviceServiceImpl;
import top.rslly.iot.utility.RuntimeMessage;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.validation.Valid;

/**
 * Hub domain controller for device management and status.
 */
@RestController
@RequestMapping(value = "/api/v2")
@Validated
public class HubTool {
  @Autowired
  private DataServiceImpl dataService;
  @Autowired
  private EventStorageServiceImpl eventStorageService;
  @Autowired
  private ProductDeviceServiceImpl productDeviceService;
  @Autowired
  private HardWareServiceImpl hardWareService;
  @Autowired
  private SafetyServiceImpl safetyService;

  @Operation(summary = "用于获取平台运行环境信息", description = "单位为百分比")
  @RequestMapping(value = "/machineMessage", method = RequestMethod.GET)
  public JsonResult<?> machineMessage() {
    return ResultTool.success(RuntimeMessage.getMessage());
  }

  @Operation(summary = "用于获取连接的设备数量", description = "仅包含当前用户绑定的设备")
  @RequestMapping(value = "/getConnectedNum", method = RequestMethod.GET)
  public JsonResult<?> getConnectedNum(@RequestHeader("Authorization") String header) {
    return ResultTool.success(productDeviceService.getProductDeviceConnectedNum(header));
  }

  @Operation(summary = "设备属性或服务控制api接口", description = "注意传入参数为ControlParam,属性或服务设置重复时候取第一个")
  @RequestMapping(value = "/control", method = RequestMethod.POST)
  public JsonResult<?> control(@Valid @RequestBody ControlParam controlParam,
      @RequestHeader("Authorization") String header) throws MqttException {
    return hardWareService.control(controlParam, header);
  }

  @Operation(summary = "用于获取物联网一段时间的设备数据", description = "时间参数请使用两个毫秒时间戳")
  @RequestMapping(value = "/readData", method = RequestMethod.POST)
  public JsonResult<?> readData(@Valid @RequestBody ReadData readData,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeDevice(header, readData.getName()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return dataService.findAllByTimeBetweenAndDeviceNameAndJsonKey(readData.getTime1(),
        readData.getTime2(),
        readData.getName(), readData.getJsonKey());
  }

  @Operation(summary = "用于获取物联网一段时间的设备事件数据", description = "时间参数请使用两个毫秒时间戳")
  @RequestMapping(value = "/readEvent", method = RequestMethod.POST)
  public JsonResult<?> readEvent(@Valid @RequestBody ReadData readData,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeDevice(header, readData.getName()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return eventStorageService.findAllByTimeBetweenAndDeviceNameAndJsonKey(readData.getTime1(),
        readData.getTime2(),
        readData.getName(), readData.getJsonKey());
  }

  @Operation(summary = "获取属性实时数据", description = "高性能接口(带redis缓存)")
  @RequestMapping(value = "/metaData", method = RequestMethod.POST)
  public JsonResult<?> metaData(@Valid @RequestBody MetaData metaData,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeDevice(header, metaData.getDeviceId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (Exception e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return dataService.metaData(metaData.getDeviceId(), metaData.getJsonKey());
  }
}
