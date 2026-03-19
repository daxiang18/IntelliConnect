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
import top.rslly.iot.dao.HubPersonaRepository;
import top.rslly.iot.dao.ProductRepository;
import top.rslly.iot.models.HubPersonaEntity;
import top.rslly.iot.models.ProductEntity;
import top.rslly.iot.param.request.HubPersona;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class HubPersonaServiceImpl implements HubPersonaService {

  @Resource
  private HubPersonaRepository hubPersonaRepository;
  @Resource
  private ProductRepository productRepository;

  @Override
  public JsonResult<?> getPersonaByProductId(int productId) {
    List<HubPersonaEntity> result = hubPersonaRepository.findAllByProductId(productId);
    return ResultTool.success(result);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> createPersona(HubPersona hubPersona) {
    List<ProductEntity> products = productRepository.findAllById(hubPersona.getProductId());
    if (products.isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    HubPersonaEntity entity = new HubPersonaEntity();
    BeanUtils.copyProperties(hubPersona, entity);
    entity.setId(0); // ensure new insert
    HubPersonaEntity saved = hubPersonaRepository.save(entity);
    return ResultTool.success(saved);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updatePersona(HubPersona hubPersona) {
    var existing = hubPersonaRepository.findById(hubPersona.getId());
    if (existing.isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    HubPersonaEntity entity = existing.get();
    entity.setPersonaName(hubPersona.getPersonaName());
    entity.setSystemPrompt(hubPersona.getSystemPrompt());
    entity.setSummaryStyle(hubPersona.getSummaryStyle());
    entity.setLanguage(hubPersona.getLanguage());
    entity.setMaxTags(hubPersona.getMaxTags());
    entity.setMaxEntities(hubPersona.getMaxEntities());
    entity.setEnabled(hubPersona.isEnabled());
    HubPersonaEntity saved = hubPersonaRepository.save(entity);
    return ResultTool.success(saved);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deletePersona(int id) {
    var existing = hubPersonaRepository.findById(id);
    if (existing.isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    List<HubPersonaEntity> result = hubPersonaRepository.deleteById(id);
    return ResultTool.success(result);
  }
}
