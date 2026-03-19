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
package top.rslly.iot.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.rslly.iot.models.InputMessageEntity;

import java.util.List;
import java.util.Optional;

public interface InputMessageRepository extends JpaRepository<InputMessageEntity, Long> {
  Optional<InputMessageEntity> findFirstByDedupeKey(String dedupeKey);

  Optional<InputMessageEntity> findFirstByDedupeKeyAndCreatedBy(String dedupeKey, String createdBy);

  List<InputMessageEntity> findAllBySessionIdOrderByReceivedAtDesc(String sessionId);

  List<InputMessageEntity> findAllBySessionIdAndCreatedByOrderByReceivedAtDesc(String sessionId,
      String createdBy);

  Page<InputMessageEntity> findAllBySessionIdAndCreatedBy(String sessionId, String createdBy, Pageable pageable);

  /** 按创建者分页查询所有消息（收件箱列表） */
  Page<InputMessageEntity> findAllByCreatedByOrderByReceivedAtDesc(String createdBy, Pageable pageable);

  /** 按创建者和来源类型分页查询（收件箱筛选） */
  Page<InputMessageEntity> findAllByCreatedByAndSourceTypeOrderByReceivedAtDesc(String createdBy, String sourceType,
      Pageable pageable);

  /** 按创建者和状态分页查询（收件箱筛选） */
  Page<InputMessageEntity> findAllByCreatedByAndStatusOrderByReceivedAtDesc(String createdBy, String status,
      Pageable pageable);

  /** 按创建者和内容类型分页查询（收件箱筛选） */
  Page<InputMessageEntity> findAllByCreatedByAndContentTypeOrderByReceivedAtDesc(String createdBy, String contentType,
      Pageable pageable);

  @Query("""
      select entity from InputMessageEntity entity
      where entity.createdBy = :createdBy
        and entity.status = :status
        and coalesce(entity.processingStartedAt, entity.receivedAt) <= :threshold
      order by coalesce(entity.processingStartedAt, entity.receivedAt) asc
      """)
  Page<InputMessageEntity> findAllByCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
      @Param("createdBy") String createdBy, @Param("status") String status, @Param("threshold") Long threshold,
      Pageable pageable);

  @Query("""
      select entity from InputMessageEntity entity
      where entity.sessionId = :sessionId
        and entity.createdBy = :createdBy
        and entity.status = :status
        and coalesce(entity.processingStartedAt, entity.receivedAt) <= :threshold
      order by coalesce(entity.processingStartedAt, entity.receivedAt) asc
      """)
  Page<InputMessageEntity> findAllBySessionIdAndCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
      @Param("sessionId") String sessionId, @Param("createdBy") String createdBy, @Param("status") String status,
      @Param("threshold") Long threshold, Pageable pageable);

  /** 通用多条件组合查询（支持关键词搜索 + 日期范围 + 文档用途 + 内容分类 + 标签） */
  @Query("""
      select e from InputMessageEntity e
      where e.createdBy = :createdBy
        and (:sourceType is null or e.sourceType = :sourceType)
        and (:status is null or e.status = :status)
        and (:contentType is null or e.contentType = :contentType)
        and (:keyword is null or lower(coalesce(e.normalizedContent, e.rawContent, '')) like lower(concat('%', :keyword, '%')))
        and (:archivedOnly = false or e.status in ('parsed', 'archived', 'synced', 'ingested'))
        and (:startTime is null or e.receivedAt >= :startTime)
        and (:endTime is null or e.receivedAt <= :endTime)
        and (:documentPurpose is null or e.documentPurpose = :documentPurpose)
        and (:contentCategory is null or e.contentCategory = :contentCategory)
        and (:contentTag is null or e.contentTags like concat('%', :contentTag, '%'))
      order by e.receivedAt desc
      """)
  Page<InputMessageEntity> searchMessages(
      @Param("createdBy") String createdBy,
      @Param("sourceType") String sourceType,
      @Param("status") String status,
      @Param("contentType") String contentType,
      @Param("keyword") String keyword,
      @Param("archivedOnly") boolean archivedOnly,
      @Param("startTime") Long startTime,
      @Param("endTime") Long endTime,
      @Param("documentPurpose") String documentPurpose,
      @Param("contentCategory") String contentCategory,
      @Param("contentTag") String contentTag,
      Pageable pageable);

  /** 按创建者统计各状态消息数量 */
  @Query("""
      select e.status, count(e) from InputMessageEntity e
      where e.createdBy = :createdBy
      group by e.status
      """)
  List<Object[]> countByStatusGrouped(@Param("createdBy") String createdBy);

  /** 按创建者统计各来源类型消息数量 */
  @Query("""
      select e.sourceType, count(e) from InputMessageEntity e
      where e.createdBy = :createdBy
      group by e.sourceType
      """)
  List<Object[]> countBySourceTypeGrouped(@Param("createdBy") String createdBy);

  /** 按创建者统计各同步状态消息数量 */
  @Query("""
      select e.syncStatus, count(e) from InputMessageEntity e
      where e.createdBy = :createdBy
        and e.syncTargets is not null and e.syncTargets <> ''
      group by e.syncStatus
      """)
  List<Object[]> countBySyncStatusGrouped(@Param("createdBy") String createdBy);

  /** 按创建者统计各文档用途消息数量 */
  @Query("""
      select e.documentPurpose, count(e) from InputMessageEntity e
      where e.createdBy = :createdBy
        and e.documentPurpose is not null and e.documentPurpose <> ''
      group by e.documentPurpose
      """)
  List<Object[]> countByDocumentPurposeGrouped(@Param("createdBy") String createdBy);

  /** 按创建者统计各内容分类（contentCategory）消息数量 */
  @Query("""
      select e.contentCategory, count(e) from InputMessageEntity e
      where e.createdBy = :createdBy
        and e.contentCategory is not null and e.contentCategory <> ''
      group by e.contentCategory
      order by count(e) desc
      """)
  List<Object[]> countByCategoryGrouped(@Param("createdBy") String createdBy);

  /** 按创建者查询分类与标签组合，用于个人中枢知识图谱构造 */
  @Query("""
      select e.contentCategory, e.contentTags from InputMessageEntity e
      where e.createdBy = :createdBy
        and ((e.contentCategory is not null and e.contentCategory <> '')
          or (e.contentTags is not null and e.contentTags <> ''))
      order by e.receivedAt desc
      """)
  List<Object[]> findCategoryAndTagsByCreatedBy(@Param("createdBy") String createdBy);

  /** 按创建者查询所有已使用的标签（contentTags 以逗号分隔，需在 Service 层拆分聚合） */
  @Query("""
      select distinct e.contentTags from InputMessageEntity e
      where e.createdBy = :createdBy
        and e.contentTags is not null and e.contentTags <> ''
      """)
  List<String> findDistinctTagsByCreatedBy(@Param("createdBy") String createdBy);

  /** 按创建者查询需要同步的消息列表（有 syncTargets 的），按同步时间倒序 */
  @Query("""
      select e from InputMessageEntity e
      where e.createdBy = :createdBy
        and e.syncTargets is not null and e.syncTargets <> ''
        and (:syncStatus is null or e.syncStatus = :syncStatus)
      order by coalesce(e.syncedAt, e.receivedAt) desc
      """)
  Page<InputMessageEntity> findSyncMessages(
      @Param("createdBy") String createdBy,
      @Param("syncStatus") String syncStatus,
      Pageable pageable);
}
