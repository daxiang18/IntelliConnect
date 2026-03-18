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

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "input_message", schema = "cwliot1.8", catalog = "")
public class InputMessageEntity {
  private long id;
  private String sourceType;
  private String sourceAccountId;
  private String sessionId;
  private String senderId;
  private String contentType;
  private String rawContent;
  private String normalizedContent;
  private String attachmentsJson;
  private String dedupeKey;
  private String status;
  private long receivedAt;
  private String createdBy;
  private String syncTargets;
  private String syncStatus;
  private Long syncedAt;
  private String externalReferencesJson;
  private Long processingStartedAt;
  private String processingAttemptToken;
  private int processingAttemptCount;
  private String documentPurpose;
  private String contentCategory;
  private String contentTags;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Basic
  @Column(name = "source_type")
  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  @Basic
  @Column(name = "source_account_id")
  public String getSourceAccountId() {
    return sourceAccountId;
  }

  public void setSourceAccountId(String sourceAccountId) {
    this.sourceAccountId = sourceAccountId;
  }

  @Basic
  @Column(name = "session_id")
  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  @Basic
  @Column(name = "sender_id")
  public String getSenderId() {
    return senderId;
  }

  public void setSenderId(String senderId) {
    this.senderId = senderId;
  }

  @Basic
  @Column(name = "content_type")
  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  @Basic
  @Column(name = "raw_content", columnDefinition = "TEXT")
  public String getRawContent() {
    return rawContent;
  }

  public void setRawContent(String rawContent) {
    this.rawContent = rawContent;
  }

  @Basic
  @Column(name = "normalized_content", columnDefinition = "TEXT")
  public String getNormalizedContent() {
    return normalizedContent;
  }

  public void setNormalizedContent(String normalizedContent) {
    this.normalizedContent = normalizedContent;
  }

  @Basic
  @Column(name = "attachments_json", columnDefinition = "TEXT")
  public String getAttachmentsJson() {
    return attachmentsJson;
  }

  public void setAttachmentsJson(String attachmentsJson) {
    this.attachmentsJson = attachmentsJson;
  }

  @Basic
  @Column(name = "dedupe_key", unique = true)
  public String getDedupeKey() {
    return dedupeKey;
  }

  public void setDedupeKey(String dedupeKey) {
    this.dedupeKey = dedupeKey;
  }

  @Basic
  @Column(name = "status")
  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Basic
  @Column(name = "received_at")
  public long getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(long receivedAt) {
    this.receivedAt = receivedAt;
  }

  @Basic
  @Column(name = "created_by")
  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  @Basic
  @Column(name = "sync_targets")
  public String getSyncTargets() {
    return syncTargets;
  }

  public void setSyncTargets(String syncTargets) {
    this.syncTargets = syncTargets;
  }

  @Basic
  @Column(name = "sync_status")
  public String getSyncStatus() {
    return syncStatus;
  }

  public void setSyncStatus(String syncStatus) {
    this.syncStatus = syncStatus;
  }

  @Basic
  @Column(name = "synced_at")
  public Long getSyncedAt() {
    return syncedAt;
  }

  public void setSyncedAt(Long syncedAt) {
    this.syncedAt = syncedAt;
  }

  @Basic
  @Column(name = "external_references_json", columnDefinition = "TEXT")
  public String getExternalReferencesJson() {
    return externalReferencesJson;
  }

  public void setExternalReferencesJson(String externalReferencesJson) {
    this.externalReferencesJson = externalReferencesJson;
  }

  @Basic
  @Column(name = "processing_started_at")
  public Long getProcessingStartedAt() {
    return processingStartedAt;
  }

  public void setProcessingStartedAt(Long processingStartedAt) {
    this.processingStartedAt = processingStartedAt;
  }

  @Basic
  @JsonIgnore
  @Column(name = "processing_attempt_token")
  public String getProcessingAttemptToken() {
    return processingAttemptToken;
  }

  public void setProcessingAttemptToken(String processingAttemptToken) {
    this.processingAttemptToken = processingAttemptToken;
  }

  @Basic
  @Column(name = "processing_attempt_count")
  public int getProcessingAttemptCount() {
    return processingAttemptCount;
  }

  public void setProcessingAttemptCount(int processingAttemptCount) {
    this.processingAttemptCount = processingAttemptCount;
  }

  @Basic
  @Column(name = "document_purpose", length = 64)
  public String getDocumentPurpose() {
    return documentPurpose;
  }

  public void setDocumentPurpose(String documentPurpose) {
    this.documentPurpose = documentPurpose;
  }

  @Basic
  @Column(name = "content_category", length = 64)
  public String getContentCategory() {
    return contentCategory;
  }

  public void setContentCategory(String contentCategory) {
    this.contentCategory = contentCategory;
  }

  @Basic
  @Column(name = "content_tags", length = 512)
  public String getContentTags() {
    return contentTags;
  }

  public void setContentTags(String contentTags) {
    this.contentTags = contentTags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    InputMessageEntity that = (InputMessageEntity) o;
    return id == that.id && receivedAt == that.receivedAt && Objects.equals(sourceType, that.sourceType)
        && Objects.equals(sourceAccountId, that.sourceAccountId) && Objects.equals(sessionId, that.sessionId)
        && Objects.equals(senderId, that.senderId) && Objects.equals(contentType, that.contentType)
        && Objects.equals(rawContent, that.rawContent)
        && Objects.equals(normalizedContent, that.normalizedContent)
        && Objects.equals(attachmentsJson, that.attachmentsJson)
        && Objects.equals(dedupeKey, that.dedupeKey) && Objects.equals(status, that.status)
        && Objects.equals(createdBy, that.createdBy)
        && Objects.equals(syncTargets, that.syncTargets)
        && Objects.equals(syncStatus, that.syncStatus)
        && Objects.equals(syncedAt, that.syncedAt)
        && Objects.equals(externalReferencesJson, that.externalReferencesJson)
        && Objects.equals(processingStartedAt, that.processingStartedAt)
        && Objects.equals(processingAttemptToken, that.processingAttemptToken)
        && processingAttemptCount == that.processingAttemptCount
        && Objects.equals(documentPurpose, that.documentPurpose)
        && Objects.equals(contentCategory, that.contentCategory)
        && Objects.equals(contentTags, that.contentTags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceType, sourceAccountId, sessionId, senderId, contentType, rawContent,
        normalizedContent, attachmentsJson, dedupeKey, status, receivedAt, createdBy, syncTargets,
        syncStatus, syncedAt, externalReferencesJson, processingStartedAt, processingAttemptToken,
        processingAttemptCount, documentPurpose, contentCategory, contentTags);
  }
}
