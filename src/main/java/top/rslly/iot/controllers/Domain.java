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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import top.rslly.iot.utility.properties.HubDomainProperty;
import top.rslly.iot.utility.properties.IotDomainProperty;
import top.rslly.iot.utility.result.JsonResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 域配置下发接口。前端启动时拉取，用于决定默认入口和菜单分组。
 */
@RestController
@RequestMapping(value = "/api/v2")
@Validated
public class Domain {

  @Autowired
  private HubDomainProperty hubDomainProperty;

  @Autowired
  private IotDomainProperty iotDomainProperty;

  @Operation(summary = "获取域配置", description = "返回 hub / iot 域的启用状态、默认入口和菜单分组")
  @RequestMapping(value = "/domain-config", method = RequestMethod.GET)
  public JsonResult<Map<String, Object>> getDomainConfig() {
    Map<String, Object> hub = new LinkedHashMap<>();
    hub.put("enabled", hubDomainProperty.isEnabled());
    hub.put("defaultEntry", hubDomainProperty.getDefaultEntry());
    hub.put("menuGroup", hubDomainProperty.getMenuGroup());

    Map<String, Object> iot = new LinkedHashMap<>();
    iot.put("enabled", iotDomainProperty.isEnabled());
    iot.put("defaultEntry", iotDomainProperty.getDefaultEntry());
    iot.put("menuGroup", iotDomainProperty.getMenuGroup());

    Map<String, Object> config = new LinkedHashMap<>();
    config.put("hub", hub);
    config.put("iot", iot);

    return new JsonResult<>(true, config);
  }
}
