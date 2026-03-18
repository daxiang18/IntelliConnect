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
package top.rslly.iot.controllers;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import top.rslly.iot.dao.TodoItemRepository;
import top.rslly.iot.models.TodoItemEntity;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

/**
 * G3: 待办事项管理 API — 查询、更新状态、删除从消息中自动提取的待办项。
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v2/todos")
public class TodoController {

  @Autowired
  private TodoItemRepository todoItemRepository;

  @Operation(summary = "查询待办列表", description = "分页查询当前用户的待办事项，可按状态筛选")
  @GetMapping
  public JsonResult<?> listTodos(
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestHeader("Authorization") String header) {
    String username = resolveUsername(header);
    if (username == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    int pageSize = Math.min(Math.max(size, 1), 100);
    Page<TodoItemEntity> result;
    if (status != null && !status.isBlank()) {
      result = todoItemRepository.findAllByCreatedByAndStatusOrderByCreatedAtDesc(
          username, status.trim(), PageRequest.of(Math.max(page, 0), pageSize));
    } else {
      result = todoItemRepository.findAllByCreatedByOrderByCreatedAtDesc(
          username, PageRequest.of(Math.max(page, 0), pageSize));
    }

    JSONObject data = new JSONObject();
    data.put("content", result.getContent());
    data.put("totalElements", result.getTotalElements());
    data.put("totalPages", result.getTotalPages());
    data.put("page", result.getNumber());
    data.put("size", result.getSize());
    return ResultTool.success(data);
  }

  @Operation(summary = "查询某消息关联的待办", description = "查询指定消息 ID 关联的所有待办项")
  @GetMapping("/by-message/{messageId}")
  public JsonResult<?> getTodosByMessage(
      @PathVariable("messageId") long messageId,
      @RequestHeader("Authorization") String header) {
    String username = resolveUsername(header);
    if (username == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    var todos = todoItemRepository.findAllByMessageIdAndCreatedByOrderByCreatedAtAsc(messageId, username);
    return ResultTool.success(todos);
  }

  @Operation(summary = "更新待办状态", description = "将待办标记为已完成或取消")
  @PutMapping("/{id}/status")
  public JsonResult<?> updateTodoStatus(
      @PathVariable("id") long id,
      @RequestParam("status") String status,
      @RequestHeader("Authorization") String header) {
    String username = resolveUsername(header);
    if (username == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    if (!java.util.Set.of("pending", "completed", "cancelled").contains(status)) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var opt = todoItemRepository.findById(id);
    if (opt.isEmpty() || !username.equals(opt.get().getCreatedBy())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }

    TodoItemEntity todo = opt.get();
    todo.setStatus(status);
    if ("completed".equals(status)) {
      todo.setCompletedAt(System.currentTimeMillis());
    } else {
      todo.setCompletedAt(null);
    }
    todoItemRepository.save(todo);
    log.info("Todo status updated: id={}, status={}, user={}", id, status, username);
    return ResultTool.success(todo);
  }

  @Operation(summary = "删除待办", description = "删除指定待办项")
  @DeleteMapping("/{id}")
  public JsonResult<?> deleteTodo(
      @PathVariable("id") long id,
      @RequestHeader("Authorization") String header) {
    String username = resolveUsername(header);
    if (username == null) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }

    var opt = todoItemRepository.findById(id);
    if (opt.isEmpty() || !username.equals(opt.get().getCreatedBy())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }

    todoItemRepository.deleteById(id);
    log.info("Todo deleted: id={}, user={}", id, username);
    return ResultTool.success();
  }

  private String resolveUsername(String token) {
    try {
      if (token == null || token.isBlank()) return null;
      if (token.startsWith("Bearer ")) token = token.substring(7);
      return JwtTokenUtil.getUsername(token);
    } catch (Exception e) {
      log.warn("Failed to resolve username from token", e);
      return null;
    }
  }
}
