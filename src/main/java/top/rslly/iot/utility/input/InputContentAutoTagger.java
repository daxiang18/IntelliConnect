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

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InputContentAutoTagger {
  private static final String CONTENT_TYPE_TEXT = "text";
  private static final String CONTENT_TYPE_URL = "url";
  private static final String CONTENT_TYPE_IMAGE = "image";
  private static final String CONTENT_TYPE_VOICE = "voice";
  private static final int MAX_TAG_COUNT = 8;
  private static final Pattern HASHTAG_PATTERN =
      Pattern.compile("(?<!\\S)#([\\p{IsAlphabetic}\\p{IsDigit}_\\-\\u4e00-\\u9fa5]{1,32})");
  private static final Pattern SCHEDULE_PATTERN =
      Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[:：]\\d{2}|今天|明天|后天|本周|下周|今晚|上午|下午)");

  private static final List<String> TASK_KEYWORDS = List.of(
      "todo", "待办", "任务", "安排", "跟进", "处理", "修复", "上线", "验收", "checklist", "提醒");
  private static final List<String> MEETING_KEYWORDS = List.of(
      "会议", "纪要", "同步", "沟通", "讨论", "review", "复盘");
  private static final List<String> IDEA_KEYWORDS = List.of(
      "想法", "灵感", "方案", "思路", "也许", "考虑", "脑暴");
  private static final List<String> KNOWLEDGE_KEYWORDS = List.of(
      "文档", "教程", "原理", "总结", "资料", "学习", "说明", "指南", "架构", "分析", "知识",
      "guide", "reference", "doc");
  private static final List<String> BUG_KEYWORDS = List.of(
      "bug", "报错", "异常", "故障", "失败", "问题", "错误");
  private static final List<String> CODE_KEYWORDS = List.of(
      "代码", "接口", "api", "sql", "java", "python", "脚本", "编译", "构建", "测试");
  private static final List<String> SYNC_KEYWORDS = List.of(
      "同步", "feishu", "github", "飞书", "推送", "发布");
  private static final List<String> QUESTION_KEYWORDS = List.of(
      "为什么", "如何", "怎么", "是否", "请问", "啥", "吗");

  public AutoTagResult analyze(String contentType, String rawContent, String normalizedContent) {
    String normalizedType = normalizeContentType(contentType);
    String baseContent = resolveBaseContent(rawContent, normalizedContent);
    String normalizedText = normalizeText(baseContent);
    String lowerCaseText = normalizedText.toLowerCase(Locale.ROOT);

    LinkedHashSet<String> tags = new LinkedHashSet<>();
    if (normalizedType != null) {
      tags.add(normalizedType);
    }
    extractSourceHost(rawContent).ifPresent(tags::add);
    tags.addAll(extractHashTags(baseContent));

    boolean hasTask = containsAny(lowerCaseText, TASK_KEYWORDS);
    boolean hasMeeting = containsAny(lowerCaseText, MEETING_KEYWORDS);
    boolean hasIdea = containsAny(lowerCaseText, IDEA_KEYWORDS);
    boolean hasKnowledge = containsAny(lowerCaseText, KNOWLEDGE_KEYWORDS);
    boolean hasBug = containsAny(lowerCaseText, BUG_KEYWORDS);
    boolean hasCode = containsAny(lowerCaseText, CODE_KEYWORDS);
    boolean hasSync = containsAny(lowerCaseText, SYNC_KEYWORDS);
    boolean hasQuestion = looksLikeQuestion(normalizedText);
    boolean hasSchedule = hasTask || SCHEDULE_PATTERN.matcher(normalizedText).find();

    String category = "note";
    if (hasTask) {
      category = "task";
      tags.add("task");
    } else if (hasMeeting) {
      category = "meeting";
      tags.add("meeting");
    } else if (hasQuestion) {
      category = "question";
      tags.add("question");
    } else if (hasIdea) {
      category = "idea";
      tags.add("idea");
    } else if (CONTENT_TYPE_URL.equals(normalizedType)) {
      category = hasKnowledge ? "knowledge" : "reference";
      tags.add(category);
    } else if (CONTENT_TYPE_IMAGE.equals(normalizedType) || CONTENT_TYPE_VOICE.equals(normalizedType)) {
      category = "media";
      tags.add("media");
    } else if (hasKnowledge) {
      category = "knowledge";
      tags.add("knowledge");
    } else {
      tags.add("note");
    }

    if (hasSchedule) {
      tags.add("schedule");
    }
    if (hasBug) {
      tags.add("bugfix");
    }
    if (hasCode) {
      tags.add("code");
    }
    if (hasSync) {
      tags.add("sync");
    }

    List<String> limitedTags = tags.stream()
        .filter(tag -> tag != null && !tag.isBlank())
        .limit(MAX_TAG_COUNT)
        .toList();
    return new AutoTagResult(category, limitedTags);
  }

  public List<String> parseTags(String rawTags) {
    if (rawTags == null || rawTags.isBlank()) {
      return List.of();
    }
    String[] parts = rawTags.split(",");
    List<String> tags = new ArrayList<>();
    for (String part : parts) {
      String normalized = part == null ? "" : part.trim();
      if (!normalized.isBlank()) {
        tags.add(normalized);
      }
    }
    return tags;
  }

  private String normalizeContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return CONTENT_TYPE_TEXT;
    }
    return contentType.trim().toLowerCase(Locale.ROOT);
  }

  private String resolveBaseContent(String rawContent, String normalizedContent) {
    if (normalizedContent != null && !normalizedContent.isBlank()) {
      return normalizedContent;
    }
    if (rawContent != null && !rawContent.isBlank()) {
      return rawContent;
    }
    return "";
  }

  private String normalizeText(String text) {
    return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
  }

  private boolean containsAny(String text, List<String> keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private boolean looksLikeQuestion(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String candidate = text.replaceAll("https?://\\S+", "").trim().toLowerCase(Locale.ROOT);
    return candidate.endsWith("?") || candidate.endsWith("？") || containsAny(candidate, QUESTION_KEYWORDS);
  }

  private Optional<String> extractSourceHost(String rawContent) {
    if (rawContent == null || rawContent.isBlank()
        || !(rawContent.startsWith("http://") || rawContent.startsWith("https://"))) {
      return Optional.empty();
    }
    try {
      String host = URI.create(rawContent).getHost();
      if (host == null || host.isBlank()) {
        return Optional.empty();
      }
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      if (normalizedHost.startsWith("www.")) {
        normalizedHost = normalizedHost.substring(4);
      }
      return Optional.of(normalizedHost);
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  private List<String> extractHashTags(String rawContent) {
    if (rawContent == null || rawContent.isBlank()) {
      return List.of();
    }
    LinkedHashSet<String> tags = new LinkedHashSet<>();
    Matcher matcher = HASHTAG_PATTERN.matcher(rawContent);
    while (matcher.find()) {
      tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return tags.stream().limit(MAX_TAG_COUNT).toList();
  }

  public record AutoTagResult(String category, List<String> tags) {
  }
}
