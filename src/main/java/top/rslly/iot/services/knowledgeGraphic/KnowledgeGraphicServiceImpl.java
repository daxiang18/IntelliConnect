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
package top.rslly.iot.services.knowledgeGraphic;

import com.alibaba.fastjson.JSON;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.rslly.iot.dao.KnowledgeGraphicAttributeRepository;
import top.rslly.iot.dao.KnowledgeGraphicNodeRepository;
import top.rslly.iot.dao.KnowledgeGraphicRelationRepository;
import top.rslly.iot.dao.InputMessageRepository;
import top.rslly.iot.models.KnowledgeGraphicAttributeEntity;
import top.rslly.iot.models.KnowledgeGraphicNodeEntity;
import top.rslly.iot.models.KnowledgeGraphicRelationEntity;
import top.rslly.iot.param.request.KnowledgeGraphicAttribute;
import top.rslly.iot.param.request.KnowledgeGraphicNode;
import top.rslly.iot.param.request.KnowledgeGraphicRelation;
import top.rslly.iot.services.knowledgeGraphic.dbo.KnowledgeGraphic;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@Slf4j
public class KnowledgeGraphicServiceImpl implements KnowledgeGraphicService {
  @Autowired
  private KnowledgeGraphicNodeRepository knowledgeGraphicNodeRepository;

  @Autowired
  private KnowledgeGraphicAttributeRepository knowledgeGraphicAttributeRepository;

  @Autowired
  private KnowledgeGraphicRelationRepository knowledgeGraphicRelationRepository;

  @Autowired
  private InputMessageRepository inputMessageRepository;

  @Autowired
  private EmbeddingModel embeddingModel;

  @Autowired
  private EmbeddingStore<TextSegment> embeddingStore;

  @Async("taskExecutor")
  public void embeddingNode(int productId, long nodeId, String nodeDes) {
    Embedding embedding = embeddingModel.embed(nodeDes).content();
    Map<String, String> metaDataMap = new HashMap<>();
    metaDataMap.put("kgNodeId", String.valueOf(nodeId));
    metaDataMap.put("kgProductId", String.valueOf(productId));
    Metadata metaData = Metadata.from(metaDataMap);
    TextSegment segment = TextSegment.from(nodeDes, metaData);
    log.info("embeddingNode: productId={}, nodeId={}", productId, nodeId);
    embeddingStore.add(embedding, segment);
  }

  public String queryKnowledgeGraphic(String query, int productId) {
    Filter filterByProductId = metadataKey("kgProductId").isEqualTo(String.valueOf(productId));
    Embedding queryEmbedding = embeddingModel.embed(query).content();
    var searchResult = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .filter(filterByProductId)
        .minScore(0.6)
        .maxResults(2)
        .build();
    EmbeddingSearchResult<TextSegment> embeddingSearchResult = embeddingStore.search(searchResult);
    List<TextSegment> matchSegment = embeddingSearchResult.matches().stream()
        .map(EmbeddingMatch::embedded)
        .toList();
    if (matchSegment.isEmpty()) {
      return null;
    }
    KnowledgeGraphic knowledgeGraphic = new KnowledgeGraphic();
    for (TextSegment segment : matchSegment) {
      try {
        Metadata metadata = segment.metadata();
        long nodeId = metadata.getLong("kgNodeId");
        KnowledgeGraphicNodeEntity node =
            knowledgeGraphicNodeRepository.findById(nodeId).orElse(null);
        KnowledgeGraphic temp = (KnowledgeGraphic) this.getKnowledgeGraphic(node, 6).getData();
        knowledgeGraphic.nodes.addAll(temp.nodes);
        knowledgeGraphic.relations.addAll(temp.relations);
      } catch (Exception e) {
        return null;
      }
    }
    return JSON.toJSONString(knowledgeGraphic);
  }

  @Async("taskExecutor")
  public void deleteKnowledgeGraphicNodeEmbedding(long nodeId) {
    Filter filter = metadataKey("kgNodeId").isEqualTo(String.valueOf(nodeId));
    embeddingStore.removeAll(filter);
  }

  @Async("taskExecutor")
  public void deleteKnowledgeGraphicNodesEmbedding(int productId) {
    Filter filter = metadataKey("kgProductId").isEqualTo(String.valueOf(productId));
    embeddingStore.removeAll(filter);
  }

  @Async("taskExecutor")
  public void updateKnowledgeGraphicNodeEmbedding(KnowledgeGraphicNodeEntity node) {
    Filter filterNodeId = metadataKey("kgNodeId").isEqualTo(String.valueOf(node.getId()));
    Filter filterProductId =
        metadataKey("kgProductId").isEqualTo(String.valueOf(node.getProductId())).and(filterNodeId);
    embeddingStore.removeAll(filterProductId);
    this.embeddingNode(node.getProductId(), node.getId(), node.getDes());
  }

  @Override
  public JsonResult<?> getKnowledgeGraphic(KnowledgeGraphicNodeEntity rootNode, int maxDepth) {
    if (rootNode == null || maxDepth <= 0) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    if (maxDepth >= 20) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    KnowledgeGraphic knowledgeGraphic = new KnowledgeGraphic();
    Stack<KnowledgeGraphicNodeEntity> nodeStack = new Stack<>();
    List<KnowledgeGraphicNodeEntity> nodeList =
        knowledgeGraphicNodeRepository.findAllByProductId(rootNode.getProductId());
    for (KnowledgeGraphicNodeEntity node : nodeList) {
      nodeStack.push(node);
      knowledgeGraphic.addNode(node.getName());
    }
    while (!nodeStack.isEmpty() && maxDepth > 0) {
      List<KnowledgeGraphicNodeEntity> nextNodeList = new ArrayList<>();
      for (KnowledgeGraphicNodeEntity node : nodeList) {
        List<KnowledgeGraphicRelationEntity> relationList =
            knowledgeGraphicRelationRepository.getAllByFrom(node.getId());
        for (KnowledgeGraphicRelationEntity relation : relationList) {
          KnowledgeGraphicNodeEntity to = this.getNodeById(relation.getTo());
          nextNodeList.add(to);
          knowledgeGraphic.addRelation(node.getName(), relation.getDes(), to.getName());
        }
        List<KnowledgeGraphicAttributeEntity> attributes =
            knowledgeGraphicAttributeRepository.getAllByBelong(node.getId());
        for (KnowledgeGraphicAttributeEntity attribute : attributes) {
          knowledgeGraphic.addAttribute(node.getName(), attribute.getName());
        }
      }
      nodeStack.clear();
      nodeList = nextNodeList;
      for (KnowledgeGraphicNodeEntity node : nodeList) {
        nodeStack.push(node);
        knowledgeGraphic.addNode(node.getName(), node.getDes());
      }
      maxDepth--;
    }
    return ResultTool.success(knowledgeGraphic);
  }

  @Override
  public String getKnowledgeGraphicJSON(int productId) {
    JsonResult<?> graphic = this.getKnowledgeGraphicByProductId(productId);
    if (null == graphic || !graphic.getSuccess())
      return "";
    KnowledgeGraphic knowledgeGraphic = (KnowledgeGraphic) graphic.getData();
    if (knowledgeGraphic == null)
      return "";
    return JSON.toJSONString(knowledgeGraphic);
  }

  @Override
  public JsonResult<?> getKnowledgeGraphicByNodeId(long id, int maxDepth) {
    KnowledgeGraphicNodeEntity node = knowledgeGraphicNodeRepository.findById(id).orElse(null);
    if (node == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return this.getKnowledgeGraphic(node, maxDepth);
  }

  @Override
  public JsonResult<?> getKnowledgeGraphicByProductId(int productId) {
    List<KnowledgeGraphicNodeEntity> nodes =
        knowledgeGraphicNodeRepository.findAllByProductId(productId);
    KnowledgeGraphic knowledgeGraphic = new KnowledgeGraphic();
    if (nodes.isEmpty())
      return ResultTool.success(knowledgeGraphic);
    for (KnowledgeGraphicNodeEntity node : nodes) {
      knowledgeGraphic.addNode(node.getName(), node.getDes());
      List<KnowledgeGraphicRelationEntity> outR =
          knowledgeGraphicRelationRepository.getAllByFrom(node.getId());
      for (KnowledgeGraphicRelationEntity relation : outR) {
        KnowledgeGraphicNodeEntity to = this.getNodeById(relation.getTo());
        knowledgeGraphic.addRelation(node.getName(), relation.getDes(), to.getName());
      }
      List<KnowledgeGraphicAttributeEntity> attributes =
          knowledgeGraphicAttributeRepository.getAllByBelong(node.getId());
      for (KnowledgeGraphicAttributeEntity attribute : attributes) {
        knowledgeGraphic.addAttribute(node.getName(), attribute.getName());
      }
    }
    return ResultTool.success(knowledgeGraphic);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addNode(KnowledgeGraphicNodeEntity node) {
    KnowledgeGraphicNodeEntity nodeDb = knowledgeGraphicNodeRepository.save(node);
    this.updateKnowledgeGraphicNodeEmbedding(nodeDb);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addNode(KnowledgeGraphicNode node) {
    KnowledgeGraphicNodeEntity nodeDb = knowledgeGraphicNodeRepository.findByName(node.name);
    if (nodeDb == null) {
      nodeDb = new KnowledgeGraphicNodeEntity();
      nodeDb.setName(node.name);
    }
    nodeDb.setDes(node.des);
    nodeDb.setProductId(node.productId);
    nodeDb = knowledgeGraphicNodeRepository.save(nodeDb);
    this.updateKnowledgeGraphicNodeEmbedding(nodeDb);
    if (!node.attributes.isEmpty()) {
      this.addAttributes(node.attributes, nodeDb.getId());
    }
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addNode(String name, String des, int productId) {
    KnowledgeGraphicNodeEntity node =
        knowledgeGraphicNodeRepository.findByNameAndProductId(name, productId);
    String historyDes;
    boolean needEmbedding = false;
    if (node == null) {
      node = new KnowledgeGraphicNodeEntity();
      node.setName(name);
      needEmbedding = true;
    } else {
      historyDes = node.getDes();
      if (!historyDes.equals(des)) {
        needEmbedding = true;
      }
    }
    node.setDes(des);
    node.setProductId(productId);
    node = knowledgeGraphicNodeRepository.save(node);
    if (needEmbedding)
      this.updateKnowledgeGraphicNodeEmbedding(node);
    return ResultTool.success(node);
  }

  @Override
  public JsonResult<?> getNode(String name, int productId) {
    KnowledgeGraphicNodeEntity node =
        knowledgeGraphicNodeRepository.findByNameAndProductId(name, productId);
    if (node == null) {
      return ResultTool.fail();
    }
    return ResultTool.success(node);
  }

  @Override
  public JsonResult<?> getNodes(int productId) {
    List<KnowledgeGraphicNodeEntity> nodeList =
        knowledgeGraphicNodeRepository.findAllByProductId(productId);
    return ResultTool.success(nodeList);
  }

  @Override
  public KnowledgeGraphicNodeEntity getNodeById(long id) {
    return knowledgeGraphicNodeRepository.findById(id).orElse(null);
  }

  @Override
  public List<KnowledgeGraphicNodeEntity> getNodesById(long id) {
    return knowledgeGraphicNodeRepository.findAllById(id);
  }

  @Override
  public JsonResult<?> getNodeRelations(String name) {
    KnowledgeGraphicNodeEntity node = knowledgeGraphicNodeRepository.findByName(name);
    if (node == null) {
      return ResultTool.fail();
    }
    List<KnowledgeGraphicRelationEntity> relations =
        knowledgeGraphicRelationRepository.getAllByFrom(node.getId());
    return ResultTool.success(relations);
  }

  @Override
  public JsonResult<?> getNodeRelations(long id) {
    List<KnowledgeGraphicRelationEntity> relations =
        knowledgeGraphicRelationRepository.getAllByTo(id);
    return ResultTool.success(relations);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addAttribute(String name, long belong) {
    KnowledgeGraphicAttributeEntity attribute =
        knowledgeGraphicAttributeRepository.getByNameAndBelong(name, belong);
    if (attribute != null) {
      return ResultTool.success();
    }
    attribute = new KnowledgeGraphicAttributeEntity();
    attribute.setName(name);
    attribute.setBelong(belong);
    knowledgeGraphicAttributeRepository.save(attribute);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addAttributes(List<String> attributes, long belong) {
    for (String attribute : attributes) {
      this.addAttribute(attribute, belong);
    }
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addAttributes(KnowledgeGraphicNode node) {
    KnowledgeGraphicNodeEntity nodeDb = knowledgeGraphicNodeRepository.findByName(node.name);
    if (nodeDb == null) {
      return ResultTool.fail();
    }
    return this.addAttributes(node.attributes, nodeDb.getId());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addAttribute(KnowledgeGraphicAttribute attribute) {
    return this.addAttribute(attribute.name, attribute.belong);
  }

  @Override
  @Deprecated
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteNode(String name) {
    KnowledgeGraphicNodeEntity node = knowledgeGraphicNodeRepository.findByName(name);
    if (node == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    knowledgeGraphicNodeRepository.delete(node);
    knowledgeGraphicRelationRepository.deleteAllByFrom(node.getId());
    knowledgeGraphicRelationRepository.deleteAllByTo(node.getId());
    knowledgeGraphicAttributeRepository.deleteByBelong(node.getId());
    this.deleteKnowledgeGraphicNodeEmbedding(node.getId());
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteNode(String name, int productId) {
    KnowledgeGraphicNodeEntity node =
        knowledgeGraphicNodeRepository.findByNameAndProductId(name, productId);
    if (node == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    knowledgeGraphicNodeRepository.delete(node);
    this.deleteKnowledgeGraphicNodeEmbedding(node.getId());
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteNode(long id) {
    KnowledgeGraphicNodeEntity node = knowledgeGraphicNodeRepository.findById(id).orElse(null);
    if (node == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    knowledgeGraphicNodeRepository.deleteById(id);
    knowledgeGraphicRelationRepository.deleteAllByFrom(id);
    knowledgeGraphicRelationRepository.deleteAllByTo(id);
    knowledgeGraphicAttributeRepository.deleteByBelong(id);
    this.deleteKnowledgeGraphicNodeEmbedding(id);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteNodes(int productId) {
    List<KnowledgeGraphicNodeEntity> nodes =
        knowledgeGraphicNodeRepository.findAllByProductId(productId);
    if (nodes.isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    for (KnowledgeGraphicNodeEntity node : nodes) {
      if (!this.deleteNode(node.getId()).getSuccess()) {
        return ResultTool.fail();
      }
    }
    this.deleteKnowledgeGraphicNodeEmbedding(productId);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateNode(KnowledgeGraphicNodeEntity node) {
    node = knowledgeGraphicNodeRepository.save(node);
    this.updateKnowledgeGraphicNodeEmbedding(node);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateNode(String name, String des, long id) {
    KnowledgeGraphicNodeEntity node = this.getNodeById(id);
    if (node == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    node.setName(name);
    node.setDes(des);
    node = knowledgeGraphicNodeRepository.save(node);
    this.updateKnowledgeGraphicNodeEmbedding(node);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateNode(KnowledgeGraphicNode node) {
    if (node.getId() <= 0)
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    KnowledgeGraphicNodeEntity nodeDb = this.getNodeById(node.getId());
    if (nodeDb == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    nodeDb.setName(node.getName());
    nodeDb.setDes(node.getDes());
    knowledgeGraphicNodeRepository.save(nodeDb);
    if (!node.attributes.isEmpty()) {
      this.addAttributes(node.attributes, nodeDb.getId());
    }
    this.updateKnowledgeGraphicNodeEmbedding(nodeDb);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addRelation(KnowledgeGraphicRelationEntity relation) {
    knowledgeGraphicRelationRepository.save(relation);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addRelation(String des, long from, long to) {
    if (this.getNodeById(from) == null || this.getNodeById(to) == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    KnowledgeGraphicRelationEntity relation =
        knowledgeGraphicRelationRepository.getByFromAndTo(from, to);
    if (relation == null) {
      relation = new KnowledgeGraphicRelationEntity();
      relation.setFrom(from);
      relation.setTo(to);
    }
    relation.setDes(des);
    knowledgeGraphicRelationRepository.save(relation);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addRelation(KnowledgeGraphicRelation relation) {
    return this.addRelation(relation.getDes(), relation.getFrom(), relation.getTo());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> addRelation(String des, String fromName, String toName, int productId) {
    KnowledgeGraphicNodeEntity fromNode =
        knowledgeGraphicNodeRepository.findByNameAndProductId(fromName, productId);
    KnowledgeGraphicNodeEntity toNode =
        knowledgeGraphicNodeRepository.findByNameAndProductId(toName, productId);
    if (fromNode == null || toNode == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return this.addRelation(des, fromNode.getId(), toNode.getId());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteRelation(KnowledgeGraphicRelationEntity relation) {
    knowledgeGraphicRelationRepository.delete(relation);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteRelation(long id) {
    knowledgeGraphicRelationRepository.deleteById(id);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteRelationsByTo(long to) {
    knowledgeGraphicRelationRepository.deleteAllByTo(to);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteRelationsByFrom(long from) {
    knowledgeGraphicRelationRepository.deleteAllByFrom(from);
    return ResultTool.success();
  }

  @Override
  public JsonResult<?> deleteRelationByFromAndTo(long from, long to) {
    KnowledgeGraphicRelationEntity relation =
        knowledgeGraphicRelationRepository.getByFromAndTo(from, to);
    if (relation == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    knowledgeGraphicRelationRepository.delete(relation);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateRelation(KnowledgeGraphicRelationEntity relation) {
    knowledgeGraphicRelationRepository.save(relation);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateRelation(String des, long id) {
    KnowledgeGraphicRelationEntity relation =
        knowledgeGraphicRelationRepository.findById(id).orElse(null);
    if (relation == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    relation.setDes(des);
    knowledgeGraphicRelationRepository.save(relation);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateRelation(KnowledgeGraphicRelation relation) {
    return this.updateRelation(relation.des, relation.id);
  }

  @Override
  public JsonResult<?> getRelationByNodes(long from, long to) {
    KnowledgeGraphicRelationEntity relation =
        knowledgeGraphicRelationRepository.getByFromAndTo(from, to);
    if (relation == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    return ResultTool.success(relation);
  }

  @Override
  @Deprecated
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteAttribute(String name) {
    knowledgeGraphicAttributeRepository.deleteAllByName(name);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteAttribute(long id) {
    knowledgeGraphicAttributeRepository.deleteById(id);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteAttribute(String name, long belong) {
    KnowledgeGraphicNodeEntity node = knowledgeGraphicNodeRepository.findById(belong).orElse(null);
    if (node == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    knowledgeGraphicAttributeRepository.deleteByBelongAndName(belong, name);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteAttribute(KnowledgeGraphicAttribute attribute) {
    KnowledgeGraphicAttributeEntity attributeDb = knowledgeGraphicAttributeRepository
        .getByNameAndBelong(attribute.getName(), attribute.getBelong());
    if (attributeDb == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    knowledgeGraphicAttributeRepository.delete(attributeDb);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteAttributesByBelong(long belong) {
    knowledgeGraphicAttributeRepository.deleteByBelong(belong);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateAttribute(String name, long id) {
    KnowledgeGraphicAttributeEntity attribute =
        knowledgeGraphicAttributeRepository.findById(id).orElse(null);
    if (attribute == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    attribute.setName(name);
    knowledgeGraphicAttributeRepository.save(attribute);
    return ResultTool.success();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> updateAttribute(String oldName, String newName, long belong) {
    KnowledgeGraphicAttributeEntity attribute =
        knowledgeGraphicAttributeRepository.getByNameAndBelong(oldName, belong);
    if (attribute == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    attribute.setName(newName);
    knowledgeGraphicAttributeRepository.save(attribute);
    return ResultTool.success();
  }

  @Override
  public JsonResult<?> getAttributes(long belong) {
    List<KnowledgeGraphicAttributeEntity> attributes =
        knowledgeGraphicAttributeRepository.findByBelong(belong);
    return ResultTool.success(attributes);
  }

  /**
   * 个人中枢知识图谱：固定包含“个人中枢”根节点，并按归档分类组织标签。
   */
  @Override
  public JsonResult<?> getHubPersonalGraphic(String username) {
    KnowledgeGraphic graph = new KnowledgeGraphic();
    String hubRoot = "个人中枢";
    graph.addNode(hubRoot, "个人中枢归档总览");

    List<Object[]> categoryRows = inputMessageRepository.countByCategoryGrouped(username);
    Map<String, Long> categoryCountMap = new LinkedHashMap<>();
    for (Object[] row : categoryRows) {
      String category = (String) row[0];
      Long count = (Long) row[1];
      if (category != null && !category.isBlank()) {
        categoryCountMap.put(category, count);
      }
    }

    Map<String, Set<String>> categoryTagsMap = new LinkedHashMap<>();
    List<Object[]> categoryTagRows = inputMessageRepository.findCategoryAndTagsByCreatedBy(username);
    for (Object[] row : categoryTagRows) {
      String category = (String) row[0];
      String rawTags = (String) row[1];
      String normalizedCategory = (category == null || category.isBlank()) ? "未分类归档" : category.trim();
      Set<String> tags = categoryTagsMap.computeIfAbsent(normalizedCategory, key -> new LinkedHashSet<>());
      if (rawTags == null || rawTags.isBlank()) {
        continue;
      }
      for (String tag : rawTags.split("[,，;；]")) {
        String trimmed = tag.trim();
        if (!trimmed.isEmpty()) {
          tags.add(trimmed);
        }
      }
    }

    if (categoryCountMap.isEmpty() && categoryTagsMap.isEmpty()) {
      return ResultTool.success(graph);
    }

    Set<String> orderedCategories = new LinkedHashSet<>();
    orderedCategories.addAll(categoryCountMap.keySet());
    orderedCategories.addAll(categoryTagsMap.keySet());

    for (String category : orderedCategories) {
      Long count = categoryCountMap.getOrDefault(category, 0L);
      graph.addNode(category, "归档分类: " + category);
      graph.addAttribute(category, "消息量: " + count);
      graph.addRelation(hubRoot, "归档", category);

      Set<String> tags = categoryTagsMap.getOrDefault(category, new LinkedHashSet<>());
      for (String tag : tags) {
        graph.addNode(tag, "标签: " + tag);
        graph.addRelation(category, "包含", tag);
      }
    }

    return ResultTool.success(graph);
  }

  /**
   * G4: 从消息 AI 分析结果中自动将提取的实体关联到知识图谱。
   */
  public void autoLinkFromMessage(List<String> entities, long messageId, String summary) {
    if (entities == null || entities.isEmpty()) {
      return;
    }

    int hubProductId = 0;
    String description = summary != null ? summary : "来自消息 #" + messageId;
    List<String> limited = entities.size() > 5 ? entities.subList(0, 5) : entities;

    List<Long> nodeIds = new ArrayList<>();
    List<String> nodeNames = new ArrayList<>();
    for (String entityName : limited) {
      if (entityName == null || entityName.isBlank()) continue;
      String trimmed = entityName.trim();
      try {
        JsonResult<?> result = this.addNode(trimmed, description, hubProductId);
        if (result.getSuccess() && result.getData() instanceof KnowledgeGraphicNodeEntity node) {
          nodeIds.add(node.getId());
          nodeNames.add(trimmed);
        }
      } catch (Exception e) {
        log.warn("autoLinkFromMessage: failed to add node '{}', messageId={}", trimmed, messageId, e);
      }
    }

    if (nodeIds.size() >= 2) {
      for (int i = 0; i < nodeIds.size(); i++) {
        for (int j = i + 1; j < nodeIds.size(); j++) {
          try {
            this.addRelation("共现于消息#" + messageId, nodeIds.get(i), nodeIds.get(j));
          } catch (Exception e) {
            log.warn("autoLinkFromMessage: failed to add relation {}-{}, messageId={}",
                nodeNames.get(i), nodeNames.get(j), messageId, e);
          }
        }
      }
    }
  }
}
