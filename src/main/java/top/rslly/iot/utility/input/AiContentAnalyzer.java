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
package top.rslly.iot.utility.input;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.rslly.iot.utility.ai.ModelMessage;
import top.rslly.iot.utility.ai.llm.LLM;
import top.rslly.iot.utility.ai.llm.LLMFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * G3: AI 内容分析器 — 使用 LLM 对输入消息进行智能分类、标签提取、摘要生成、待办识别和实体提取。
 * 当 LLM 调用失败或超时时，降级为规则分类（InputContentAutoTagger）。
 */
@Slf4j
@Component
public class AiContentAnalyzer {

  @Value("${ai.classifierTool-llm:silicon-Qwen/Qwen3-Next-80B-A3B-Instruct}")
  private String llmName;

  /** AI 分析超时（秒），默认 30 秒 */
  @Value("${hub.ai-analyzer.timeout-seconds:30}")
  private int timeoutSeconds;

  /** 最小文本长度阈值 — 低于此长度跳过 AI 分析 */
  @Value("${hub.ai-analyzer.min-text-length:20}")
  private int minTextLength;

  /** 最大文本长度 — 超过此长度截断 */
  @Value("${hub.ai-analyzer.max-text-length:2000}")
  private int maxTextLength;

  private static final String SYSTEM_PROMPT = """
      你是一个个人知识管理助手。请分析以下用户输入内容，返回严格 JSON 格式（不要输出 markdown 代码块）：

      {
        "category": "分类",
        "tags": ["标签1", "标签2"],
        "summary": "一句话摘要",
        "todos": [
          {"content": "待办内容", "priority": "high/medium/low"}
        ],
        "entities": ["实体1", "实体2"]
      }

      分类规则（category 必须是以下之一）：
      - task: 包含明确的任务、待办事项、TODO
      - meeting: 会议记录、会议安排
      - question: 提问、疑问
      - idea: 创意、灵感、想法
      - knowledge: 知识点、学习笔记、技术文档
      - reference: 参考资料、链接收藏
      - note: 日常记录、随手笔记
      - schedule: 日程、时间安排

      标签规则（tags）：
      - 提取 3-5 个关键主题标签
      - 使用中文标签
      - 如果内容涉及技术，标注技术名称

      摘要规则（summary）：
      - 用一句话概括内容核心
      - 不超过 50 字

      待办提取规则（todos）：
      - 只提取明确的行动项
      - 如果没有待办，返回空数组
      - priority: high=紧急/重要, medium=一般, low=可选

      实体提取规则（entities）：
      - 提取人名、组织、技术名词、项目名等关键实体
      - 最多 5 个
      - 如果没有明确实体，返回空数组
      """;

  /**
   * AI 分析结果
   */
  public record AnalysisResult(
      String category,
      List<String> tags,
      String summary,
      List<TodoItem> todos,
      List<String> entities,
      boolean fromAi) {
  }

  /**
   * 待办项
   */
  public record TodoItem(
      String content,
      String priority) {
  }

  /**
   * 对输入内容进行 AI 分析。
   * 如果文本太短或 LLM 失败，返回 null（调用方降级为规则分类）。
   */
  public AnalysisResult analyze(String contentType, String rawContent, String normalizedContent) {
    String text = normalizedContent != null && !normalizedContent.isBlank()
        ? normalizedContent : rawContent;
    if (text == null || text.length() < minTextLength) {
      log.debug("AI analysis skipped: text too short ({} chars)", text == null ? 0 : text.length());
      return null;
    }

    // 截断过长文本
    String truncatedText = text.length() > maxTextLength ? text.substring(0, maxTextLength) + "..." : text;

    String userPrompt = String.format("内容类型：%s\n内容：\n%s",
        contentType != null ? contentType : "text", truncatedText);

    try {
      LLM llm = LLMFactory.getLLM(llmName);
      List<ModelMessage> messages = new ArrayList<>();
      messages.add(new ModelMessage("system", SYSTEM_PROMPT));

      // 使用带超时的方式调用
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<String> future = executor.submit(() -> llm.commonChat(userPrompt, messages, false));

      String response;
      try {
        response = future.get(timeoutSeconds, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        future.cancel(true);
        log.warn("AI analysis timed out after {}s", timeoutSeconds);
        return null;
      } finally {
        executor.shutdownNow();
      }

      if (response == null || response.isBlank()) {
        log.warn("AI analysis returned empty response");
        return null;
      }

      return parseResponse(response);
    } catch (Exception e) {
      log.warn("AI analysis failed: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 解析 LLM 返回的 JSON 响应
   */
  private AnalysisResult parseResponse(String response) {
    try {
      // 尝试提取 JSON（LLM 可能返回 markdown 代码块）
      String json = extractJson(response);
      JSONObject obj = JSON.parseObject(json);

      String category = obj.getString("category");
      if (category == null || category.isBlank()) {
        category = "note";
      }

      JSONArray tagsArray = obj.getJSONArray("tags");
      List<String> tags = new ArrayList<>();
      if (tagsArray != null) {
        for (int i = 0; i < Math.min(tagsArray.size(), 8); i++) {
          String tag = tagsArray.getString(i);
          if (tag != null && !tag.isBlank()) {
            tags.add(tag.trim());
          }
        }
      }

      String summary = obj.getString("summary");
      if (summary != null && summary.length() > 200) {
        summary = summary.substring(0, 200);
      }

      JSONArray todosArray = obj.getJSONArray("todos");
      List<TodoItem> todos = new ArrayList<>();
      if (todosArray != null) {
        for (int i = 0; i < Math.min(todosArray.size(), 10); i++) {
          JSONObject todoObj = todosArray.getJSONObject(i);
          if (todoObj != null) {
            String content = todoObj.getString("content");
            String priority = todoObj.getString("priority");
            if (content != null && !content.isBlank()) {
              if (priority == null || !List.of("high", "medium", "low").contains(priority)) {
                priority = "medium";
              }
              todos.add(new TodoItem(content.trim(), priority));
            }
          }
        }
      }

      JSONArray entitiesArray = obj.getJSONArray("entities");
      List<String> entities = new ArrayList<>();
      if (entitiesArray != null) {
        for (int i = 0; i < Math.min(entitiesArray.size(), 5); i++) {
          String entity = entitiesArray.getString(i);
          if (entity != null && !entity.isBlank()) {
            entities.add(entity.trim());
          }
        }
      }

      return new AnalysisResult(category, tags, summary, todos, entities, true);
    } catch (Exception e) {
      log.warn("Failed to parse AI analysis response: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 从 LLM 响应中提取 JSON（处理可能的 markdown 代码块包裹）
   */
  private String extractJson(String response) {
    String trimmed = response.trim();
    // 移除可能的 ```json ... ``` 包裹
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      int lastBacktick = trimmed.lastIndexOf("```");
      if (firstNewline > 0 && lastBacktick > firstNewline) {
        trimmed = trimmed.substring(firstNewline + 1, lastBacktick).trim();
      }
    }
    // 提取第一个 { 到最后一个 }
    int start = trimmed.indexOf('{');
    int end = trimmed.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return trimmed.substring(start, end + 1);
    }
    return trimmed;
  }
}
