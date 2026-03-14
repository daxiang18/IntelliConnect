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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Data
public class InputAttachmentParam {
  public static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;

  private static final String HTTP_URL_PATTERN = "(?i)^https?://\\S+$";
  private static final String MIME_TYPE_PATTERN = "^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$";

  @NotBlank(message = "name 不能为空")
  @Size(min = 1, max = 255, message = "name 长度必须在 1 到 255 之间")
  private String name;

  @NotBlank(message = "url 不能为空")
  @Size(min = 1, max = 2048, message = "url 长度必须在 1 到 2048 之间")
  @Pattern(regexp = HTTP_URL_PATTERN, message = "url 必须是有效的 http/https 地址")
  private String url;

  @NotBlank(message = "contentType 不能为空")
  @Size(min = 1, max = 128, message = "contentType 长度必须在 1 到 128 之间")
  @Pattern(regexp = MIME_TYPE_PATTERN, message = "contentType 必须是有效的 MIME 类型")
  private String contentType;

  @PositiveOrZero(message = "size 不能为负数")
  @Max(value = MAX_SIZE_BYTES, message = "size 不能超过 52428800 字节")
  private Long size;
}
