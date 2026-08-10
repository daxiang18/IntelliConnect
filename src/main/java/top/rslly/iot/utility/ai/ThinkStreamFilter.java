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
package top.rslly.iot.utility.ai;

/**
 * 流式思考块过滤器（有状态，单会话单实例，非线程安全）。
 * <p>
 * 推理模型（MiniMax M 系列 / DeepSeek-R1 等）会把 {@code <think>内心独白</think>} 内联在正文里。
 * 流式场景下标签可能被拆到不同分片（例如 {@code "<thi"} + {@code "nk>"}），
 * 因此不能对单个分片做正则替换，必须跨分片维持状态。
 * <p>
 * 语音设备若不过滤，会把整段独白念给用户听。
 */
public final class ThinkStreamFilter {

  private static final String OPEN = "<think";
  private static final String CLOSE = "</think>";
  /** 需要挂起的最大尾巴长度：可能是被截断的标签前缀 */
  private static final int MAX_TAIL = CLOSE.length();

  private final StringBuilder pending = new StringBuilder();
  private boolean inThink = false;

  /**
   * 送入一个流式分片，返回其中可以安全输出的文本（可能为空字符串）。
   */
  public String feed(String chunk) {
    if (chunk == null || chunk.isEmpty()) {
      return "";
    }
    pending.append(chunk);
    StringBuilder out = new StringBuilder();

    while (true) {
      if (inThink) {
        int end = pending.indexOf(CLOSE);
        if (end < 0) {
          // 思考块未结束：只保留可能是闭标签前缀的尾巴，其余丢弃
          keepOnlyTail();
          break;
        }
        pending.delete(0, end + CLOSE.length());
        inThink = false;
      } else {
        int start = pending.indexOf(OPEN);
        if (start < 0) {
          // 无开标签：输出安全部分，尾巴留待下轮（可能是被截断的标签）
          int safe = pending.length() - MAX_TAIL;
          if (safe > 0) {
            out.append(pending, 0, safe);
            pending.delete(0, safe);
          }
          break;
        }
        out.append(pending, 0, start);
        pending.delete(0, start);
        // 等待完整的开标签（形如 <think> 或 <think ...>）
        int gt = pending.indexOf(">");
        if (gt < 0) {
          break;
        }
        pending.delete(0, gt + 1);
        inThink = true;
      }
    }
    return out.toString();
  }

  /**
   * 流结束时调用：吐出残留的安全文本（未闭合的思考块内容一律丢弃）。
   */
  public String flush() {
    if (inThink) {
      pending.setLength(0);
      return "";
    }
    String rest = pending.toString();
    pending.setLength(0);
    int dangling = rest.indexOf(OPEN);
    return dangling >= 0 ? rest.substring(0, dangling) : rest;
  }

  private void keepOnlyTail() {
    if (pending.length() > MAX_TAIL) {
      pending.delete(0, pending.length() - MAX_TAIL);
    }
  }
}
