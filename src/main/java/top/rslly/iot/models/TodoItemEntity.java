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
import lombok.Data;

/**
 * G3: 待办事项实体 — 从输入消息中自动提取的待办项，支持独立查询和管理。
 */
@Data
@Entity
@Table(name = "todo_item", schema = "cwliot1.8")
public class TodoItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private long id;

  /** 关联的消息 ID */
  @Column(name = "message_id")
  private long messageId;

  /** 待办内容 */
  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  /** 优先级：high / medium / low */
  @Column(name = "priority", length = 16)
  private String priority;

  /** 状态：pending / completed / cancelled */
  @Column(name = "status", length = 32)
  private String status;

  /** 所属用户 */
  @Column(name = "created_by", length = 255)
  private String createdBy;

  /** 创建时间 */
  @Column(name = "created_at")
  private long createdAt;

  /** 完成时间 */
  @Column(name = "completed_at")
  private Long completedAt;
}
