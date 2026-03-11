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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import top.rslly.iot.models.*;
import top.rslly.iot.services.agent.*;
import top.rslly.iot.services.iot.AlarmEventServiceImpl;
import top.rslly.iot.services.iot.OtaPassiveServiceImpl;
import top.rslly.iot.services.iot.OtaServiceImpl;
import top.rslly.iot.services.knowledgeGraphic.KnowledgeGraphicService;
import top.rslly.iot.services.thingsModel.*;
import top.rslly.iot.services.wechat.WxProductBindServiceImpl;
import top.rslly.iot.services.wechat.WxUserServiceImpl;
import top.rslly.iot.utility.JwtTokenUtil;

import java.util.List;

@Service
@Slf4j
public class SafetyServiceImpl implements SafetyService {
  @Autowired
  @Nullable
  private WxProductBindServiceImpl wxProductBindService;
  @Autowired
  @Nullable
  private ProductModelServiceImpl productModelService;
  @Autowired
  @Nullable
  private ProductDeviceServiceImpl productDeviceService;
  @Autowired
  @Nullable
  private ProductEventServiceImpl productEventService;
  @Autowired
  @Nullable
  private UserProductBindServiceImpl userProductBindService;
  @Autowired
  @Nullable
  private ProductFunctionServiceImpl productFunctionService;
  @Autowired
  @Nullable
  private EventDataServiceImpl eventDataService;
  @Autowired
  @Nullable
  private ProductDataServiceImpl productDataService;
  @Autowired
  @Nullable
  private ProductRoleServiceImpl productRoleService;
  @Autowired
  @Nullable
  private AlarmEventServiceImpl alarmEventService;
  @Autowired
  @Nullable
  private UserServiceImpl userService;
  @Autowired
  @Nullable
  private WxUserServiceImpl wxUserService;
  @Autowired
  @Nullable
  private McpServerServiceImpl mcpServerService;
  @Autowired
  @Nullable
  private OtaServiceImpl otaService;
  @Autowired
  @Nullable
  private OtaXiaozhiServiceImpl otaXiaozhiService;
  @Autowired
  @Nullable
  private OtaPassiveServiceImpl otaPassiveService;
  @Autowired
  @Nullable
  private KnowledgeChatServiceImpl knowledgeChatService;
  @Autowired
  @Nullable
  private ProductRouterSetServiceImpl productRouterSetService;
  @Autowired
  @Nullable
  private OtaXiaozhiPassiveServiceImpl otaXiaozhiPassiveService;
  @Autowired
  @Nullable
  private AgentLongMemoryServiceImpl agentLongMemoryService;
  @Autowired
  @Nullable
  private ProductVoiceDiyServiceImpl productVoiceDiyService;
  @Autowired
  @Nullable
  private AgentMemoryServiceImpl agentMemoryService;
  @Autowired
  @Nullable
  private KnowledgeGraphicService knowledgeGraphicService;
  @Autowired
  @Nullable
  private LlmProviderInformationServiceImpl llmProviderInformationService;
  @Autowired
  @Nullable
  private ProductLlmModelServiceImpl productLlmModelService;
  @Autowired
  @Nullable
  private ProductSkillsServiceImpl productSkillsService;

  private boolean checkServiceNotNull(Object service, String serviceName) {
    if (service == null) {
      log.warn("{} is not available. IoT domain may be disabled.", serviceName);
      return false;
    }
    return true;
  }

  @Override
  public boolean controlAuthorizeModel(String token, int modelId) {
    if (!checkServiceNotNull(productModelService, "ProductModelService")) {
      return false;
    }
    List<ProductModelEntity> productModelEntityList = productModelService.findAllById(modelId);
    if (productModelEntityList.isEmpty())
      throw new IllegalArgumentException("modelId not found!");
    return this.controlAuthorizeProduct(token,
        productModelEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeDevice(String token, int deviceId) {
    if (!checkServiceNotNull(productDeviceService, "ProductDeviceService")) {
      return false;
    }
    List<ProductDeviceEntity> productDeviceEntityList = productDeviceService.findAllById(deviceId);
    if (productDeviceEntityList.isEmpty())
      throw new IllegalArgumentException("deviceId not found!");
    return this.controlAuthorizeModel(token,
        productDeviceEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeDevice(String token, String deviceName) {
    if (!checkServiceNotNull(productDeviceService, "ProductDeviceService")) {
      return false;
    }
    List<ProductDeviceEntity> productDeviceEntityList =
        productDeviceService.findAllByName(deviceName);
    if (productDeviceEntityList.isEmpty())
      throw new IllegalArgumentException("deviceName not found!");
    return this.controlAuthorizeModel(token,
        productDeviceEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeFunction(String token, int functionId) {
    if (!checkServiceNotNull(productFunctionService, "ProductFunctionService")) {
      return false;
    }
    List<ProductFunctionEntity> productFunctionEntityList =
        productFunctionService.findAllById(functionId);
    if (productFunctionEntityList.isEmpty())
      throw new IllegalArgumentException("functionId not found!");
    return this.controlAuthorizeModel(token,
        productFunctionEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeEvent(String token, int eventId) {
    if (!checkServiceNotNull(productEventService, "ProductEventService")) {
      return false;
    }
    List<ProductEventEntity> productEventEntityList = productEventService.findAllById(eventId);
    if (productEventEntityList.isEmpty())
      throw new IllegalArgumentException("eventId not found!");
    return this.controlAuthorizeModel(token,
        productEventEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeAlarmEvent(String token, int alarmEventId) {
    if (!checkServiceNotNull(alarmEventService, "AlarmEventService")) {
      return false;
    }
    List<AlarmEventEntity> alarmEventEntityList = alarmEventService.findAllById(alarmEventId);
    if (alarmEventEntityList.isEmpty())
      throw new IllegalArgumentException("alarmEventId not found!");
    return this.controlAuthorizeEvent(token,
        alarmEventEntityList.get(0).getEventId());
  }

  @Override
  public boolean controlAuthorizeEventData(String token, int eventDataId) {
    if (!checkServiceNotNull(eventDataService, "EventDataService")) {
      return false;
    }
    List<EventDataEntity> eventDataEntityList = eventDataService.findAllById(eventDataId);
    if (eventDataEntityList.isEmpty())
      throw new IllegalArgumentException("eventDataId not found!");
    return this.controlAuthorizeModel(token,
        eventDataEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeProductData(String token, int productDataId) {
    if (!checkServiceNotNull(productDataService, "ProductDataService")) {
      return false;
    }
    List<ProductDataEntity> productDataEntityList = productDataService.findAllById(productDataId);
    if (productDataEntityList.isEmpty())
      throw new IllegalArgumentException("productDataId not found!");
    return this.controlAuthorizeModel(token,
        productDataEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeProductRole(String token, int productRoleId) {
    if (!checkServiceNotNull(productRoleService, "ProductRoleService")) {
      return false;
    }
    List<ProductRoleEntity> productRoleEntityList = productRoleService.findAllById(productRoleId);
    if (productRoleEntityList.isEmpty())
      throw new IllegalArgumentException("productRoleId not found!");
    return this.controlAuthorizeProduct(token,
        productRoleEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeMcpServer(String token, int mcpServerId) {
    if (!checkServiceNotNull(mcpServerService, "McpServerService")) {
      return false;
    }
    List<McpServerEntity> mcpServerEntityList = mcpServerService.findALLById(mcpServerId);
    if (mcpServerEntityList.isEmpty())
      throw new IllegalArgumentException("mcpServerId not found!");
    return this.controlAuthorizeProduct(token,
        mcpServerEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeOta(String token, int id) {
    if (!checkServiceNotNull(otaService, "OtaService")) {
      return false;
    }
    List<OtaEntity> otaEntityList = otaService.findAllById(id);
    if (otaEntityList.isEmpty())
      throw new IllegalArgumentException("otaId not found!");
    return this.controlAuthorizeProduct(token,
        otaEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeOtaXiaoZhi(String token, int id) {
    if (!checkServiceNotNull(otaXiaozhiService, "OtaXiaozhiService")) {
      return false;
    }
    List<OtaXiaozhiEntity> otaXiaozhiEntityList = otaXiaozhiService.findAllById(id);
    if (otaXiaozhiEntityList.isEmpty())
      throw new IllegalArgumentException("otaXiaozhiId not found!");
    return this.controlAuthorizeProduct(token,
        otaXiaozhiEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeOta(String token, String name, String deviceName) {
    if (!checkServiceNotNull(productDeviceService, "ProductDeviceService")) {
      return false;
    }
    if (!checkServiceNotNull(productModelService, "ProductModelService")) {
      return false;
    }
    if (!checkServiceNotNull(otaService, "OtaService")) {
      return false;
    }
    var productDeviceEntityList = productDeviceService.findAllByName(deviceName);
    if (productDeviceEntityList.isEmpty())
      throw new IllegalArgumentException("deviceName not found!");
    int modelId = productDeviceEntityList.get(0).getModelId();
    int productId = productModelService.findAllById(modelId).get(0).getProductId();
    List<OtaEntity> otaEntityList = otaService.findAllByProductIdAndName(productId, name);
    if (otaEntityList.isEmpty())
      throw new IllegalArgumentException("otaName not found!");
    return this.controlAuthorizeProduct(token, productId);
  }

  @Override
  public boolean controlAuthorizeXiaoZhiOtaPassive(String token, int id) {
    if (!checkServiceNotNull(otaXiaozhiPassiveService, "OtaXiaozhiPassiveService")) {
      return false;
    }
    List<OtaXiaozhiPassiveEntity> otaXiaozhiPassiveEntityList =
        otaXiaozhiPassiveService.findAllById(id);
    if (otaXiaozhiPassiveEntityList.isEmpty())
      throw new IllegalArgumentException("otaXiaozhiPassiveId not found!");
    return this.controlAuthorizeProduct(token,
        otaXiaozhiPassiveEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeOtaPassive(String token, int id) {
    if (!checkServiceNotNull(otaPassiveService, "OtaPassiveService")) {
      return false;
    }
    List<OtaPassiveEntity> otaPassiveEntityList = otaPassiveService.findAllById(id);
    if (otaPassiveEntityList.isEmpty())
      throw new IllegalArgumentException("otaPassiveId not found!");
    return this.controlAuthorizeDevice(token,
        otaPassiveEntityList.get(0).getDeviceId());
  }

  @Override
  public boolean controlAuthorizeKnowledgeChat(String token, int id) {
    if (!checkServiceNotNull(knowledgeChatService, "KnowledgeChatService")) {
      return false;
    }
    List<KnowledgeChatEntity> knowledgeChatEntityList = knowledgeChatService.findAllById(id);
    if (knowledgeChatEntityList.isEmpty())
      throw new IllegalArgumentException("knowledgeChatId not found!");
    return this.controlAuthorizeProduct(token,
        knowledgeChatEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeKnowledgeGraphicNode(String token, int id) {
    if (!checkServiceNotNull(knowledgeGraphicService, "KnowledgeGraphicService")) {
      return false;
    }
    List<KnowledgeGraphicNodeEntity> knowledgeGraphicNodeEntities =
        knowledgeGraphicService.getNodesById(id);
    if (knowledgeGraphicNodeEntities.isEmpty())
      throw new IllegalArgumentException("knowledgeGraphicNodeId not found!");
    return this.controlAuthorizeProduct(token,
        knowledgeGraphicNodeEntities.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeProductRouterSet(String token, int id) {
    if (!checkServiceNotNull(productRouterSetService, "ProductRouterSetService")) {
      return false;
    }
    List<ProductRouterSetEntity> productRouterSetEntityList =
        productRouterSetService.findAllById(id);
    if (productRouterSetEntityList.isEmpty())
      throw new IllegalArgumentException("productRouterSetId not found!");
    return this.controlAuthorizeProduct(token,
        productRouterSetEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeAgentLongMemory(String token, int id) {
    if (!checkServiceNotNull(agentLongMemoryService, "AgentLongMemoryService")) {
      return false;
    }
    List<AgentLongMemoryEntity> agentLongMemoryEntityList =
        agentLongMemoryService.findAllById(id);
    if (agentLongMemoryEntityList.isEmpty())
      throw new IllegalArgumentException("agentLongMemoryId not found!");
    return this.controlAuthorizeProduct(token,
        agentLongMemoryEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeProductVoiceDiy(String token, int id) {
    if (!checkServiceNotNull(productVoiceDiyService, "ProductVoiceDiyService")) {
      return false;
    }
    List<ProductVoiceDiyEntity> productVoiceDiyEntityList =
        productVoiceDiyService.findAllById(id);
    if (productVoiceDiyEntityList.isEmpty())
      throw new IllegalArgumentException("productVoiceDiyId not found!");
    return this.controlAuthorizeProduct(token,
        productVoiceDiyEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeAgentMemory(String token, int id) {
    if (!checkServiceNotNull(agentMemoryService, "AgentMemoryService")) {
      return false;
    }
    if (!checkServiceNotNull(wxUserService, "WxUserService")) {
      return false;
    }
    if (!checkServiceNotNull(wxProductBindService, "WxProductBindService")) {
      return false;
    }
    if (!checkServiceNotNull(userService, "UserService")) {
      return false;
    }
    if (!checkServiceNotNull(userProductBindService, "UserProductBindService")) {
      return false;
    }
    String token_deal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    String role = JwtTokenUtil.getUserRole(token_deal);
    String username = JwtTokenUtil.getUsername(token_deal);
    List<AgentMemoryEntity> agentMemoryEntityList = agentMemoryService.findAllById(id);
    if (agentMemoryEntityList.isEmpty())
      throw new IllegalArgumentException("agentMemoryId not found!");
    String memoryChatId = agentMemoryEntityList.get(0).getChatId();
    if (role.equals("ROLE_" + "wx_user")) {
      if (wxUserService.findAllByName(username).isEmpty()) {
        return false;
      }
      List<WxUserEntity> wxUserEntityList = wxUserService.findAllByName(username);
      String appid = wxUserEntityList.get(0).getAppid();
      String openid = wxUserEntityList.get(0).getOpenid();
      if (memoryChatId.equals(appid + openid)) {
        return true;
      }
      List<WxProductBindEntity> wxProductBindEntityList =
          wxProductBindService.findAllByAppidAndOpenid(appid, openid);
      for (WxProductBindEntity bind : wxProductBindEntityList) {
        if (memoryChatId.startsWith("chatProduct" + bind.getProductId())) {
          return true;
        }
      }
      return false;
    } else if (!role.equals("[ROLE_admin]")) {
      var userList = userService.findAllByUsername(username);
      if (userList.isEmpty())
        return false;
      int userId = userList.get(0).getId();
      List<UserProductBindEntity> userProductBindEntityList =
          userProductBindService.findAllByUserId(userId);
      for (UserProductBindEntity bind : userProductBindEntityList) {
        if (memoryChatId.startsWith("chatProduct" + bind.getProductId())) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @Override
  public boolean controlAuthorizeAgentMemory(String token, String chatId) {
    if (!checkServiceNotNull(agentMemoryService, "AgentMemoryService")) {
      return false;
    }
    List<AgentMemoryEntity> agentMemoryEntityList = agentMemoryService.findAllByChatId(chatId);
    if (agentMemoryEntityList.isEmpty())
      throw new IllegalArgumentException("chatId not found!");
    return this.controlAuthorizeAgentMemory(token,
        agentMemoryEntityList.get(0).getId());
  }

  @Override
  public boolean controlAuthorizeLlmProviderInformation(String token, int id) {
    if (!checkServiceNotNull(llmProviderInformationService, "LlmProviderInformationService")) {
      return false;
    }
    String token_deal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    String role = JwtTokenUtil.getUserRole(token_deal);
    String username = JwtTokenUtil.getUsername(token_deal);
    if (role.equals("[ROLE_admin]"))
      return true;
    else {
      List<LlmProviderInformationEntity> llmProviderInformationEntityList =
          llmProviderInformationService.findAllById(id);
      if (llmProviderInformationEntityList.isEmpty()) {
        throw new IllegalArgumentException("llmProviderInformationId not found!");
      } else {
        return username.equals(llmProviderInformationEntityList.get(0).getUserName());
      }
    }
  }

  @Override
  public boolean controlAuthorizeProductLlmModel(String token, int id) {
    if (!checkServiceNotNull(productLlmModelService, "ProductLlmModelService")) {
      return false;
    }
    List<ProductLlmModelEntity> productLlmModelEntityList = productLlmModelService.findAllById(id);
    if (productLlmModelEntityList.isEmpty())
      throw new IllegalArgumentException("productLlmModelId not found!");
    return this.controlAuthorizeProduct(token,
        productLlmModelEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeProductSkills(String token, int productSkillsId) {
    if (!checkServiceNotNull(productSkillsService, "ProductSkillsService")) {
      return false;
    }
    List<ProductSkillsEntity> productSkillsEntityList =
        productSkillsService.findAllById(productSkillsId);
    if (productSkillsEntityList.isEmpty())
      throw new IllegalArgumentException("productSkillsId not found!");
    return this.controlAuthorizeProduct(token,
        productSkillsEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeProduct(String token, int productId) {
    if (!checkServiceNotNull(wxUserService, "WxUserService")) {
      return false;
    }
    if (!checkServiceNotNull(wxProductBindService, "WxProductBindService")) {
      return false;
    }
    if (!checkServiceNotNull(userService, "UserService")) {
      return false;
    }
    if (!checkServiceNotNull(userProductBindService, "UserProductBindService")) {
      return false;
    }
    String token_deal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    String role = JwtTokenUtil.getUserRole(token_deal);
    String username = JwtTokenUtil.getUsername(token_deal);
    if (role.equals("ROLE_" + "wx_user")) {
      if (wxUserService.findAllByName(username).isEmpty()) {
        return false;
      }
      List<WxUserEntity> wxUserEntityList = wxUserService.findAllByName(username);
      String appid = wxUserEntityList.get(0).getAppid();
      String openid = wxUserEntityList.get(0).getOpenid();
      // log.info("productId{}",productId);
      return !wxProductBindService.findByAppidAndOpenidAndProductId(appid, openid, productId)
          .isEmpty();
    } else if (!role.equals("[ROLE_admin]")) {
      var userList = userService.findAllByUsername(username);
      if (userList.isEmpty())
        return false;
      int userId = userList.get(0).getId();
      return !userProductBindService.findAllByUserIdAndProductId(userId, productId).isEmpty();
    }
    return true;
  }
}
