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
package top.rslly.iot.models;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "hub_pipeline_config", schema = "cwliot1.8", catalog = "")
public class HubPipelineConfigEntity {

  private int id;
  private int productId;
  private boolean autoProcess;
  private boolean urlNormalize;
  private boolean imageVision;
  private boolean voiceAsr;
  private boolean aiAnalysis;
  private boolean todoExtraction;
  private boolean entityExtraction;
  private boolean vectorIngest;
  private boolean knowledgeGraphLink;
  private boolean feishuSync;
  private boolean githubSync;
  private String routingPrompt;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  @Basic
  @Column(name = "product_id", unique = true)
  public int getProductId() {
    return productId;
  }

  public void setProductId(int productId) {
    this.productId = productId;
  }

  @Basic
  @Column(name = "auto_process")
  public boolean isAutoProcess() {
    return autoProcess;
  }

  public void setAutoProcess(boolean autoProcess) {
    this.autoProcess = autoProcess;
  }

  @Basic
  @Column(name = "url_normalize")
  public boolean isUrlNormalize() {
    return urlNormalize;
  }

  public void setUrlNormalize(boolean urlNormalize) {
    this.urlNormalize = urlNormalize;
  }

  @Basic
  @Column(name = "image_vision")
  public boolean isImageVision() {
    return imageVision;
  }

  public void setImageVision(boolean imageVision) {
    this.imageVision = imageVision;
  }

  @Basic
  @Column(name = "voice_asr")
  public boolean isVoiceAsr() {
    return voiceAsr;
  }

  public void setVoiceAsr(boolean voiceAsr) {
    this.voiceAsr = voiceAsr;
  }

  @Basic
  @Column(name = "ai_analysis")
  public boolean isAiAnalysis() {
    return aiAnalysis;
  }

  public void setAiAnalysis(boolean aiAnalysis) {
    this.aiAnalysis = aiAnalysis;
  }

  @Basic
  @Column(name = "todo_extraction")
  public boolean isTodoExtraction() {
    return todoExtraction;
  }

  public void setTodoExtraction(boolean todoExtraction) {
    this.todoExtraction = todoExtraction;
  }

  @Basic
  @Column(name = "entity_extraction")
  public boolean isEntityExtraction() {
    return entityExtraction;
  }

  public void setEntityExtraction(boolean entityExtraction) {
    this.entityExtraction = entityExtraction;
  }

  @Basic
  @Column(name = "vector_ingest")
  public boolean isVectorIngest() {
    return vectorIngest;
  }

  public void setVectorIngest(boolean vectorIngest) {
    this.vectorIngest = vectorIngest;
  }

  @Basic
  @Column(name = "knowledge_graph_link")
  public boolean isKnowledgeGraphLink() {
    return knowledgeGraphLink;
  }

  public void setKnowledgeGraphLink(boolean knowledgeGraphLink) {
    this.knowledgeGraphLink = knowledgeGraphLink;
  }

  @Basic
  @Column(name = "feishu_sync")
  public boolean isFeishuSync() {
    return feishuSync;
  }

  public void setFeishuSync(boolean feishuSync) {
    this.feishuSync = feishuSync;
  }

  @Basic
  @Column(name = "github_sync")
  public boolean isGithubSync() {
    return githubSync;
  }

  public void setGithubSync(boolean githubSync) {
    this.githubSync = githubSync;
  }

  @Basic
  @Column(name = "routing_prompt", columnDefinition = "TEXT")
  public String getRoutingPrompt() {
    return routingPrompt;
  }

  public void setRoutingPrompt(String routingPrompt) {
    this.routingPrompt = routingPrompt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    HubPipelineConfigEntity that = (HubPipelineConfigEntity) o;
    return id == that.id && productId == that.productId && autoProcess == that.autoProcess
        && urlNormalize == that.urlNormalize && imageVision == that.imageVision
        && voiceAsr == that.voiceAsr && aiAnalysis == that.aiAnalysis
        && todoExtraction == that.todoExtraction && entityExtraction == that.entityExtraction
        && vectorIngest == that.vectorIngest && knowledgeGraphLink == that.knowledgeGraphLink
        && feishuSync == that.feishuSync && githubSync == that.githubSync
        && Objects.equals(routingPrompt, that.routingPrompt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, productId, autoProcess, urlNormalize, imageVision, voiceAsr,
        aiAnalysis, todoExtraction, entityExtraction, vectorIngest, knowledgeGraphLink,
        feishuSync, githubSync, routingPrompt);
  }
}
