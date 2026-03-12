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
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.rslly.iot.param.request.*;
import top.rslly.iot.services.SafetyServiceImpl;
import top.rslly.iot.services.agent.*;
import top.rslly.iot.services.agent.AiServiceImpl;
import top.rslly.iot.services.knowledgeGraphic.KnowledgeGraphicService;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;

/**
 * Hub domain controller for knowledge, memory, graph, LLM provider, MCP, skills, and tool-ban
 * endpoints. Available in both hub-only and combined hub+iot deployments.
 */
@RestController
@RequestMapping(value = "/api/v2")
@Validated
public class HubTool {

  @Autowired
  private SafetyServiceImpl safetyService;
  @Autowired
  private KnowledgeChatServiceImpl knowledgeChatService;
  @Autowired
  private ProductToolsBanServiceImpl productToolsBanService;
  @Autowired
  private AgentLongMemoryServiceImpl agentLongMemoryService;
  @Autowired
  private ProductVoiceDiyServiceImpl productVoiceDiyService;
  @Autowired
  private AgentMemoryServiceImpl agentMemoryService;
  @Autowired
  private LlmProviderInformationServiceImpl llmProviderInformationService;
  @Autowired
  private ProductLlmModelServiceImpl productLlmModelService;
  @Autowired
  private KnowledgeGraphicService knowledgeGraphicService;
  @Autowired
  private ProductSkillsServiceImpl productSkillsService;
  @Autowired
  private AiServiceImpl aiService;

  // ── Knowledge Chat ──────────────────────────────────────────────────────────

  @Operation(summary = "获取知识库", description = "获取当前用户的知识库列表")
  @RequestMapping(value = "/knowledgeChat", method = RequestMethod.GET)
  public JsonResult<?> getKnowledgeChat(@RequestHeader("Authorization") String header) {
    return knowledgeChatService.getKnowledgeChat(header);
  }

  @Operation(summary = "知识库", description = "搜索产品的知识库")
  @RequestMapping(value = "/knowledgeChatRecall", method = RequestMethod.POST)
  public JsonResult<?> searchKnowledgeChat(
      @Valid @RequestBody KnowledgeChatRecall knowledgeChatRecall,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, knowledgeChatRecall.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeChatService.searchByProductId(knowledgeChatRecall.getProductId(),
        knowledgeChatRecall.getQuery());
  }

  @Operation(summary = "知识库", description = "提交产品的知识库")
  @RequestMapping(value = "/knowledgeChat", method = RequestMethod.POST)
  public JsonResult<?> postKnowledgeChat(@RequestParam("productId") int productId,
      @RequestParam("filename") @jakarta.validation.constraints.NotBlank(message = "filename 不能为空")
      @Size(min = 1, max = 255, message = "fileName 长度必须在 1 到 255 之间") String fileName,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile multipartFile,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeChatService.postKnowledgeChat(productId, fileName, multipartFile);
  }

  @RequestMapping(value = "/knowledgeChat", method = RequestMethod.DELETE)
  public JsonResult<?> deleteKnowledgeChat(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeKnowledgeChat(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeChatService.deleteKnowledgeChat(id);
  }

  // ── MCP Endpoint ────────────────────────────────────────────────────────────

  @Operation(summary = "mcp接入点url获取", description = "获取mcp接入点url")
  @RequestMapping(value = "/mcpEndpoint", method = RequestMethod.GET)
  public JsonResult<?> getMcpPointUrl(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return aiService.getMcpPointUrl(productId);
  }

  @Operation(summary = "mcp接入点工具获取", description = "获取mcp接入点工具详情")
  @RequestMapping(value = "/mcpEndpoint/tools", method = RequestMethod.GET)
  public JsonResult<?> getMcpPointTools(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return aiService.getMcpPointTools(productId);
  }

  // ── Product Tools Ban ───────────────────────────────────────────────────────

  @Operation(summary = "禁止内部工具", description = "获取当前产品工具禁用列表")
  @RequestMapping(value = "/productToolsBan", method = RequestMethod.GET)
  public JsonResult<?> getProductToolsBan(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productToolsBanService.getProductToolsBan(productId);
  }

  @Operation(summary = "查询工具是否被禁止", description = "查询当前产品的某个工具是否被禁止")
  @RequestMapping(value = "/productToolsBan/{toolsName}", method = RequestMethod.GET)
  public JsonResult<?> getProductToolsBanByName(@PathVariable String toolsName,
      @RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productToolsBanService.getProductToolsBanByNameAndProductId(toolsName, productId);
  }

  @Operation(summary = "禁止内部工具", description = "禁止内部工具")
  @RequestMapping(value = "/productToolsBan", method = RequestMethod.POST)
  public JsonResult<?> postProductToolsBan(
      @Valid @RequestBody ProductToolsBan productToolsBan,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productToolsBan.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productToolsBanService.postProductToolsBan(productToolsBan);
  }

  @Operation(summary = "解禁全部工具", description = "解禁全部工具")
  @RequestMapping(value = "/productToolsBan", method = RequestMethod.DELETE)
  public JsonResult<?> deleteProductToolsBan(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productToolsBanService.deleteProductToolsBan(productId);
  }

  @Operation(summary = "禁止单个工具", description = "禁止单个工具")
  @RequestMapping(value = "/productToolsBanSingle", method = RequestMethod.POST)
  public JsonResult<?> postProductToolsBanSingle(@RequestParam("productId") int productId,
      @RequestParam("toolName") @Valid @NotNull @Size(min = 1, max = 255) String toolName,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productToolsBanService.addProductToolBan(toolName, productId);
  }

  @Operation(summary = "解禁单个工具", description = "解禁单个工具")
  @RequestMapping(value = "/productToolsBanSingle", method = RequestMethod.DELETE)
  public JsonResult<?> deleteProductToolsBanSingle(@RequestParam("productId") int productId,
      @RequestParam("toolName") @Valid @NotNull @Size(min = 1, max = 255) String toolName,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productToolsBanService.deleteProductToolBan(toolName, productId);
  }

  // ── Long Memory ─────────────────────────────────────────────────────────────

  @Operation(summary = "获取长期记忆", description = "获取长期记忆")
  @RequestMapping(value = "/longMemory", method = RequestMethod.GET)
  public JsonResult<?> getLongMemory(@RequestHeader("Authorization") String header) {
    return agentLongMemoryService.getLongMemory(header);
  }

  @Operation(summary = "提交/修改长期记忆详情", description = "提交/修改长期记忆")
  @RequestMapping(value = "/longMemory", method = RequestMethod.POST)
  public JsonResult<?> postLongMemory(
      @Valid @RequestBody AgentLongMemory agentLongMemory,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, agentLongMemory.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return agentLongMemoryService.postLongMemory(agentLongMemory);
  }

  @Operation(summary = "删除长期记忆", description = "删除长期记忆")
  @RequestMapping(value = "/longMemory", method = RequestMethod.DELETE)
  public JsonResult<?> deleteLongMemory(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeAgentLongMemory(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return agentLongMemoryService.deleteLongMemory(id);
  }

  // ── Product Voice Diy ───────────────────────────────────────────────────────

  @Operation(summary = "获取产品语音定制", description = "获取产品语音定制")
  @RequestMapping(value = "/productVoiceDiy", method = RequestMethod.GET)
  public JsonResult<?> getProductVoiceDiy(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productVoiceDiyService.getProductVoiceDiy(productId);
  }

  @Operation(summary = "提交/修改产品语音定制", description = "提交/修改产品语音定制")
  @RequestMapping(value = "/productVoiceDiy", method = RequestMethod.POST)
  public JsonResult<?> postProductVoiceDiy(
      @Valid @RequestBody ProductVoiceDiy productVoiceDiy,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productVoiceDiy.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productVoiceDiyService.postProductVoiceDiy(productVoiceDiy);
  }

  @Operation(summary = "删除产品语音定制", description = "删除产品语音定制")
  @RequestMapping(value = "/productVoiceDiy", method = RequestMethod.DELETE)
  public JsonResult<?> deleteProductVoiceDiy(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProductVoiceDiy(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productVoiceDiyService.deleteProductVoiceDiy(id);
  }

  // ── Chat Memory ─────────────────────────────────────────────────────────────

  @Operation(summary = "获取聊天记忆", description = "获取聊天记忆")
  @RequestMapping(value = "/memory", method = RequestMethod.GET)
  public JsonResult<?> getMemory(@RequestHeader("Authorization") String header) {
    return agentMemoryService.getMemory(header);
  }

  @Operation(summary = "修改聊天记忆", description = "修改聊天记忆")
  @RequestMapping(value = "/memory", method = RequestMethod.PUT)
  public JsonResult<?> updateMemory(
      @Valid @RequestBody AgentMemory agentMemory,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeAgentMemory(header, agentMemory.getChatId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return agentMemoryService.updateMemory(agentMemory);
  }

  @Operation(summary = "删除聊天记忆", description = "删除聊天记忆")
  @RequestMapping(value = "/memory", method = RequestMethod.DELETE)
  public JsonResult<?> deleteMemory(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeAgentMemory(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return agentMemoryService.deleteMemory(id);
  }

  // ── LLM Provider ────────────────────────────────────────────────────────────

  @Operation(summary = "获取LLM提供商信息",
      description = "获取LLM提供商信息，管理员可查看所有，普通用户只能查看自己的")
  @RequestMapping(value = "/llmProviderInformation", method = RequestMethod.GET)
  public JsonResult<?> getLlmProviderInformation(@RequestHeader("Authorization") String header) {
    return llmProviderInformationService.getLLmProviderInformation(header);
  }

  @Operation(summary = "创建或更新LLM提供商信息", description = "创建或更新LLM提供商信息")
  @RequestMapping(value = "/llmProviderInformation", method = RequestMethod.POST)
  public JsonResult<?> postLlmProviderInformation(
      @Valid @RequestBody LlmProviderInformation llmProviderInformation,
      @RequestHeader("Authorization") String header) {
    return llmProviderInformationService.postLLmProviderInformation(header, llmProviderInformation);
  }

  @Operation(summary = "删除LLM提供商信息", description = "根据ID删除LLM提供商信息")
  @RequestMapping(value = "/llmProviderInformation", method = RequestMethod.DELETE)
  public JsonResult<?> deleteLlmProviderInformation(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeLlmProviderInformation(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return llmProviderInformationService.deleteLLmProviderInformation(id);
  }

  // ── Product LLM Model ───────────────────────────────────────────────────────

  @Operation(summary = "获取产品LLM模型配置", description = "获取产品LLM模型配置")
  @RequestMapping(value = "/productLlmModel", method = RequestMethod.GET)
  public JsonResult<?> getProductLlmModel(@RequestHeader("Authorization") String header) {
    return productLlmModelService.getProductLlmModel(header);
  }

  @Operation(summary = "获取产品LLM模型配置", description = "获取产品LLM模型配置(产品id)")
  @RequestMapping(value = "/productLlmModelByProductId", method = RequestMethod.GET)
  public JsonResult<?> getProductLlmModelByProductId(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.success(new ArrayList<>());
    }
    return productLlmModelService.getProductLlmModelByProductId(productId);
  }

  @Operation(summary = "创建或更新产品LLM模型配置", description = "创建或更新产品LLM模型配置")
  @RequestMapping(value = "/productLlmModel", method = RequestMethod.POST)
  public JsonResult<?> postProductLlmModel(
      @Valid @RequestBody ProductLlmModel productLlmModel,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productLlmModel.getProductId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
      if (!safetyService.controlAuthorizeLlmProviderInformation(header,
          productLlmModel.getProviderId()))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productLlmModelService.postProductLlmModel(productLlmModel);
  }

  @Operation(summary = "删除产品LLM模型配置", description = "根据ID删除产品LLM模型配置")
  @RequestMapping(value = "/productLlmModel", method = RequestMethod.DELETE)
  public JsonResult<?> deleteProductLlmModel(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProductLlmModel(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productLlmModelService.deleteProductLlmModel(id);
  }

  // ── Product Skills ──────────────────────────────────────────────────────────

  @Operation(summary = "获取产品技能", description = "获取产品技能列表")
  @RequestMapping(value = "/productSkills", method = RequestMethod.GET)
  public JsonResult<?> getProductSkill(@RequestHeader("Authorization") String header) {
    return productSkillsService.getProductSkill(header);
  }

  @Operation(summary = "添加产品技能", description = "上传产品技能文件")
  @RequestMapping(value = "/productSkills", method = RequestMethod.POST,
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public JsonResult<?> addProductSkill(
      @RequestParam("productId") @Min(value = 1, message = "productId 必须大于 0") int productId,
      @RequestPart("file") @NotNull(message = "file 不能为空") MultipartFile multipartFile,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productSkillsService.addProductSkill(productId, multipartFile);
  }

  @Operation(summary = "删除产品技能", description = "根据ID删除产品技能")
  @RequestMapping(value = "/productSkills", method = RequestMethod.DELETE)
  public JsonResult<?> deleteProductSkill(@RequestParam("id") int id,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProductSkills(header, id))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return productSkillsService.deleteProductSkill(id);
  }

  // ── Knowledge Graph ─────────────────────────────────────────────────────────

  @Operation(summary = "获取知识图谱", description = "通过产品ID获取该产品的知识图谱")
  @RequestMapping(value = "/kg/graphic", method = RequestMethod.GET)
  public JsonResult<?> getKnowledgeGraphic(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.getKnowledgeGraphicByProductId(productId);
  }

  @Operation(summary = "添加知识图谱节点", description = "添加知识图谱节点")
  @RequestMapping(value = "/kg/node", method = RequestMethod.POST)
  public JsonResult<?> addKgNode(@Valid @RequestBody KnowledgeGraphicNode node,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, node.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.addNode(node);
  }

  @Operation(summary = "获取知识图谱节点", description = "通过节点名称获取节点")
  @RequestMapping(value = "/kg/node", method = RequestMethod.GET)
  public JsonResult<?> getKgNode(@RequestParam("name") String name,
      @RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.getNode(name, productId);
  }

  @Operation(summary = "获取知识图谱节点列表", description = "获取产品下的所有知识图谱节点")
  @RequestMapping(value = "/kg/nodes", method = RequestMethod.GET)
  public JsonResult<?> getKgNodes(@RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.getNodes(productId);
  }

  @Operation(summary = "更新知识图谱节点", description = "更新节点，但只更新节点的基本属性（名称、描述）")
  @RequestMapping(value = "/kg/node", method = RequestMethod.PUT)
  public JsonResult<?> updateNode(@RequestBody KnowledgeGraphicNode node,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, node.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.updateNode(node);
  }

  @Operation(summary = "删除知识图谱节点", description = "通过节点ID删除知识图谱节点")
  @RequestMapping(value = "/kg/node", method = RequestMethod.DELETE)
  public JsonResult<?> deleteKgNodeById(@Valid @RequestBody KnowledgeGraphicNode node,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, node.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.deleteNode(node.id);
  }

  @Operation(summary = "删除知识图谱节点", description = "通过节点名称删除知识图谱节点")
  @RequestMapping(value = "/kg/nodeByName", method = RequestMethod.DELETE)
  public JsonResult<?> deleteKgNodeByName(@RequestParam("name") String name,
      @RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.deleteNode(name, productId);
  }

  @Operation(summary = "新增属性", description = "为节点新增属性")
  @RequestMapping(value = "/kg/attr", method = RequestMethod.POST)
  public JsonResult<?> addAttribute(@Valid @RequestBody KnowledgeGraphicAttribute attribute,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, attribute.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.addAttribute(attribute);
  }

  @Operation(summary = "删除属性", description = "删除节点的属性")
  @RequestMapping(value = "/kg/attr", method = RequestMethod.DELETE)
  public JsonResult<?> deleteAttribute(@RequestBody KnowledgeGraphicAttribute attribute,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, attribute.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.deleteAttribute(attribute);
  }

  @Operation(summary = "更新属性名称", description = "更新属性名称")
  @RequestMapping(value = "/kg/attr", method = RequestMethod.PUT)
  public JsonResult<?> updateAttributeByName(
      @Valid @RequestBody KnowledgeGraphicAttribute attribute,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, attribute.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.updateAttribute(attribute.name, attribute.id);
  }

  @Operation(summary = "获取节点属性", description = "获取节点的所有属性")
  @RequestMapping(value = "/kg/attr", method = RequestMethod.GET)
  public JsonResult<?> getAttributesByNodeId(@RequestParam("nodeId") long nodeId,
      @RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.getAttributes(nodeId);
  }

  @Operation(summary = "新增关系", description = "新增节点关系")
  @RequestMapping(value = "/kg/relation", method = RequestMethod.POST)
  public JsonResult<?> addRelation(@Valid @RequestBody KnowledgeGraphicRelation relation,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, relation.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.addRelation(relation);
  }

  @Operation(summary = "删除关系", description = "通过关系ID删除节点关系")
  @RequestMapping(value = "/kg/relationById", method = RequestMethod.DELETE)
  public JsonResult<?> deleteRelation(@RequestBody KnowledgeGraphicRelation relation,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, relation.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.deleteRelation(relation.id);
  }

  @Operation(summary = "删除关系", description = "通过关系两端的节点删除关系")
  @RequestMapping(value = "/kg/relation", method = RequestMethod.DELETE)
  public JsonResult<?> deleteRelationByNodes(@RequestBody KnowledgeGraphicRelation relation,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, relation.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.deleteRelationByFromAndTo(relation.from, relation.to);
  }

  @Operation(summary = "更新关系描述", description = "通过关系ID更新节点关系描述")
  @RequestMapping(value = "/kg/relation", method = RequestMethod.PUT)
  public JsonResult<?> updateRelation(@RequestBody KnowledgeGraphicRelation relation,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, relation.productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.updateRelation(relation);
  }

  @Operation(summary = "获取源节点的关系", description = "通过源节点的ID获取关系列表")
  @RequestMapping(value = "/kg/relation", method = RequestMethod.GET)
  public JsonResult<?> getRelations(@RequestParam("nodeId") long nodeId,
      @RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.getNodeRelations(nodeId);
  }

  @Operation(summary = "获取关系", description = "通过关系两端的节点获取关系")
  @RequestMapping(value = "/kg/relationByNodes", method = RequestMethod.GET)
  public JsonResult<?> getRelationByNodes(@RequestParam("from") long from,
      @RequestParam("to") long to,
      @RequestParam("productId") int productId,
      @RequestHeader("Authorization") String header) {
    try {
      if (!safetyService.controlAuthorizeProduct(header, productId))
        return ResultTool.fail(ResultCode.NO_PERMISSION);
    } catch (NullPointerException e) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return knowledgeGraphicService.getRelationByNodes(from, to);
  }
}
