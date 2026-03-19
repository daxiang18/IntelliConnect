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
@Table(name = "hub_persona", schema = "cwliot1.8", catalog = "")
public class HubPersonaEntity {

  private int id;
  private int productId;
  private String personaName;
  private String systemPrompt;
  private String summaryStyle;
  private String language;
  private int maxTags;
  private int maxEntities;
  private boolean enabled;

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
  @Column(name = "product_id")
  public int getProductId() {
    return productId;
  }

  public void setProductId(int productId) {
    this.productId = productId;
  }

  @Basic
  @Column(name = "persona_name", length = 100)
  public String getPersonaName() {
    return personaName;
  }

  public void setPersonaName(String personaName) {
    this.personaName = personaName;
  }

  @Basic
  @Column(name = "system_prompt", columnDefinition = "TEXT")
  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  @Basic
  @Column(name = "summary_style", length = 50)
  public String getSummaryStyle() {
    return summaryStyle;
  }

  public void setSummaryStyle(String summaryStyle) {
    this.summaryStyle = summaryStyle;
  }

  @Basic
  @Column(name = "language", length = 20)
  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  @Basic
  @Column(name = "max_tags")
  public int getMaxTags() {
    return maxTags;
  }

  public void setMaxTags(int maxTags) {
    this.maxTags = maxTags;
  }

  @Basic
  @Column(name = "max_entities")
  public int getMaxEntities() {
    return maxEntities;
  }

  public void setMaxEntities(int maxEntities) {
    this.maxEntities = maxEntities;
  }

  @Basic
  @Column(name = "enabled")
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    HubPersonaEntity that = (HubPersonaEntity) o;
    return id == that.id && productId == that.productId && maxTags == that.maxTags
        && maxEntities == that.maxEntities && enabled == that.enabled
        && Objects.equals(personaName, that.personaName)
        && Objects.equals(systemPrompt, that.systemPrompt)
        && Objects.equals(summaryStyle, that.summaryStyle)
        && Objects.equals(language, that.language);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, productId, personaName, systemPrompt, summaryStyle, language, maxTags,
        maxEntities, enabled);
  }
}
