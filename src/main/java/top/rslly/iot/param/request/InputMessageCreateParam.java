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
package top.rslly.iot.param.request;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class InputMessageCreateParam {
  @NotBlank(message = "sourceType 不能为空")
  @Size(min = 1, max = 64, message = "sourceType 长度必须在 1 到 64 之间")
  private String sourceType;

  @Size(max = 255, message = "sourceAccountId 长度不能超过 255")
  private String sourceAccountId;

  @NotBlank(message = "sessionId 不能为空")
  @Size(min = 1, max = 255, message = "sessionId 长度必须在 1 到 255 之间")
  private String sessionId;

  @Size(max = 255, message = "senderId 长度不能超过 255")
  private String senderId;

  @NotBlank(message = "contentType 不能为空")
  @Size(min = 1, max = 64, message = "contentType 长度必须在 1 到 64 之间")
  private String contentType;

  @NotBlank(message = "rawContent 不能为空")
  @Size(min = 1, max = 10000, message = "rawContent 长度必须在 1 到 10000 之间")
  private String rawContent;

  @Size(max = 10000, message = "normalizedContent 长度不能超过 10000")
  private String normalizedContent;

  @Valid
  private List<InputAttachmentParam> attachments;

  @NotBlank(message = "dedupeKey 不能为空")
  @Size(min = 1, max = 255, message = "dedupeKey 长度必须在 1 到 255 之间")
  private String dedupeKey;

  @Size(max = 64, message = "status 长度不能超过 64")
  private String status;

  private Long receivedAt;
}
