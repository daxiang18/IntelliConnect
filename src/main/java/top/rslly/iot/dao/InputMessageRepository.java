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
}
