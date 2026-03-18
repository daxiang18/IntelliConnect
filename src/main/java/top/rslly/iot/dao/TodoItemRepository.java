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
import org.springframework.stereotype.Repository;
import top.rslly.iot.models.TodoItemEntity;

import java.util.List;

/**
 * G3: 待办事项 Repository
 */
@Repository
public interface TodoItemRepository extends JpaRepository<TodoItemEntity, Long> {

  /** 查询用户所有待办（分页） */
  Page<TodoItemEntity> findAllByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);

  /** 查询用户指定状态的待办（分页） */
  Page<TodoItemEntity> findAllByCreatedByAndStatusOrderByCreatedAtDesc(String createdBy, String status, Pageable pageable);

  /** 查询某消息关联的待办 */
  List<TodoItemEntity> findAllByMessageIdAndCreatedByOrderByCreatedAtAsc(long messageId, String createdBy);

  /** 删除某消息关联的所有待办 */
  void deleteAllByMessageId(long messageId);
}
