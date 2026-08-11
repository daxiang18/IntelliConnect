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
package top.rslly.iot.utility.ai.voice.TTS;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话情绪上下文。
 * <p>
 * EmotionTool 已经为每轮对话判定了情绪（原本只用于给设备下发表情），这里把它按 chatId
 * 暂存，供 TTS 合成时带上情绪参数，让语音有喜怒哀乐而不是平板朗读。
 * <p>
 * 服务端情绪词表有 25 个，MiniMax T2A 只认 8 个（happy/sad/angry/fearful/disgusted/
 * surprised/neutral/calm），故需做映射。
 */
public final class TtsEmotionContext {

  private static final Map<String, String> EMOTION_BY_CHAT = new ConcurrentHashMap<>();

  /** 服务端情绪词 -> MiniMax 支持的情绪 */
  private static final Map<String, String> TO_MINIMAX = Map.ofEntries(
      Map.entry("happy", "happy"),
      Map.entry("laughing", "happy"),
      Map.entry("funny", "happy"),
      Map.entry("loving", "happy"),
      Map.entry("kissy", "happy"),
      Map.entry("winking", "happy"),
      Map.entry("silly", "happy"),
      Map.entry("delicious", "happy"),
      Map.entry("confident", "happy"),
      Map.entry("cool", "calm"),
      Map.entry("relaxed", "calm"),
      Map.entry("sleepy", "calm"),
      Map.entry("thinking", "calm"),
      Map.entry("neutral", "neutral"),
      Map.entry("sad", "sad"),
      Map.entry("crying", "sad"),
      Map.entry("embarrassed", "sad"),
      Map.entry("angry", "angry"),
      Map.entry("surprised", "surprised"),
      Map.entry("shocked", "surprised"),
      Map.entry("confused", "surprised"));

  private TtsEmotionContext() {}

  public static void put(String chatId, String emotion) {
    if (chatId == null || emotion == null || emotion.isBlank()) {
      return;
    }
    String mapped = TO_MINIMAX.get(emotion.trim().toLowerCase());
    if (mapped != null) {
      EMOTION_BY_CHAT.put(chatId, mapped);
    }
  }

  /** 返回该会话当前情绪；未知返回 null（调用方不带 emotion 参数即可） */
  public static String get(String chatId) {
    return chatId == null ? null : EMOTION_BY_CHAT.get(chatId);
  }

  public static void remove(String chatId) {
    if (chatId != null) {
      EMOTION_BY_CHAT.remove(chatId);
    }
  }
}
