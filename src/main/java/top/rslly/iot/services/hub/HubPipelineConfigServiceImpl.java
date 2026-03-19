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
package top.rslly.iot.services.hub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.rslly.iot.dao.HubPipelineConfigRepository;
import top.rslly.iot.dao.ProductRepository;
import top.rslly.iot.models.HubPipelineConfigEntity;
import top.rslly.iot.models.ProductEntity;
import top.rslly.iot.param.request.HubPipelineConfig;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class HubPipelineConfigServiceImpl implements HubPipelineConfigService {

  @Resource
  private HubPipelineConfigRepository hubPipelineConfigRepository;
  @Resource
  private ProductRepository productRepository;

  @Override
  public JsonResult<?> getConfigByProductId(int productId) {
    List<HubPipelineConfigEntity> result =
        hubPipelineConfigRepository.findAllByProductId(productId);
    if (result.isEmpty()) {
      // Return default config when none exists
      HubPipelineConfigEntity defaultConfig = buildDefaultConfig(productId);
      return ResultTool.success(defaultConfig);
    }
    return ResultTool.success(result.get(0));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> createConfig(HubPipelineConfig hubPipelineConfig) {
    List<ProductEntity> products =
        productRepository.findAllById(hubPipelineConfig.getProductId());
    if (products.isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    // Check if config already exists for this product
    List<HubPipelineConfigEntity> existing =
        hubPipelineConfigRepository.findAllByProductId(hubPipelineConfig.getProductId());
    if (!existing.isEmpty()) {
      // Update instead of duplicate create
      HubPipelineConfigEntity entity = existing.get(0);
      copyConfigProperties(hubPipelineConfig, entity);
      HubPipelineConfigEntity saved = hubPipelineConfigRepository.save(entity);
      return ResultTool.success(saved);
    }
    HubPipelineConfigEntity entity = new HubPipelineConfigEntity();
    copyConfigProperties(hubPipelineConfig, entity);
    entity.setId(0); // ensure new insert
    HubPipelineConfigEntity saved = hubPipelineConfigRepository.save(entity);
    return ResultTool.success(saved);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateConfig(HubPipelineConfig hubPipelineConfig) {
    List<HubPipelineConfigEntity> existing =
        hubPipelineConfigRepository.findAllByProductId(hubPipelineConfig.getProductId());
    HubPipelineConfigEntity entity;
    if (existing.isEmpty()) {
      // Create new if not exists
      entity = new HubPipelineConfigEntity();
      entity.setId(0);
    } else {
      entity = existing.get(0);
    }
    copyConfigProperties(hubPipelineConfig, entity);
    HubPipelineConfigEntity saved = hubPipelineConfigRepository.save(entity);
    return ResultTool.success(saved);
  }

  private void copyConfigProperties(HubPipelineConfig source, HubPipelineConfigEntity target) {
    target.setProductId(source.getProductId());
    target.setAutoProcess(source.isAutoProcess());
    target.setUrlNormalize(source.isUrlNormalize());
    target.setImageVision(source.isImageVision());
    target.setVoiceAsr(source.isVoiceAsr());
    target.setAiAnalysis(source.isAiAnalysis());
    target.setTodoExtraction(source.isTodoExtraction());
    target.setEntityExtraction(source.isEntityExtraction());
    target.setVectorIngest(source.isVectorIngest());
    target.setKnowledgeGraphLink(source.isKnowledgeGraphLink());
    target.setFeishuSync(source.isFeishuSync());
    target.setGithubSync(source.isGithubSync());
    target.setRoutingPrompt(source.getRoutingPrompt());
  }

  private HubPipelineConfigEntity buildDefaultConfig(int productId) {
    HubPipelineConfigEntity config = new HubPipelineConfigEntity();
    config.setProductId(productId);
    config.setAutoProcess(true);
    config.setUrlNormalize(true);
    config.setImageVision(true);
    config.setVoiceAsr(true);
    config.setAiAnalysis(true);
    config.setTodoExtraction(true);
    config.setEntityExtraction(true);
    config.setVectorIngest(true);
    config.setKnowledgeGraphLink(false);
    config.setFeishuSync(false);
    config.setGithubSync(false);
    config.setRoutingPrompt(null);
    return config;
  }
}
