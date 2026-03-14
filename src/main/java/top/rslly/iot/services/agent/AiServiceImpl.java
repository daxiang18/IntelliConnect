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
package top.rslly.iot.services.agent;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.rslly.iot.param.request.AiControl;
import top.rslly.iot.services.SafetyServiceImpl;
import top.rslly.iot.services.thingsModel.ProductServiceImpl;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.MyFileUtil;
import top.rslly.iot.utility.RedisUtil;
import top.rslly.iot.utility.ai.chain.Router;
import top.rslly.iot.utility.ai.llm.LLMFactory;
import top.rslly.iot.utility.ai.mcp.McpWebsocket;
import top.rslly.iot.utility.ai.voice.ASR.Audio2Text;
import top.rslly.iot.utility.ai.voice.AudioUtils;
import top.rslly.iot.utility.ai.voice.TTS.Text2audio;
import top.rslly.iot.utility.ai.voice.TTS.TtsService;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Service
@Slf4j
public class AiServiceImpl implements AiService {
  @Value("${ai.audio-tmp-path}")
  private String audioPath;
  @Value("${ai.audio-temp-url}")
  private String audioTempUrl;
  @Value("${ai.vision-model}")
  private String visionModel;
  @Value("${ota.xiaozhi.url}")
  private String otaUrl;
  @Autowired
  private Router router;
  @Autowired
  private TtsService text2audio;
  @Autowired
  private Audio2Text audio2Text;
  @Autowired
  private ProductServiceImpl productService;
  @Autowired
  private SafetyServiceImpl safetyService;
  @Autowired
  private RedisUtil redisUtil;
  private static final String prefix_url = "/api/v2/ai/tmp_voice";

  @Override
  public JsonResult<?> getAiResponse(AiControl aiControl, String token) {
    if (!safetyService.controlAuthorizeProduct(token, aiControl.getProductId())) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }
    if (productService.findAllById(aiControl.getProductId()).isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    String chatId = "chatProduct" + aiControl.getProductId();
    var answer =
        router.response(aiControl.getContent(), chatId, aiControl.getProductId());
    return ResultTool.success(answer);
  }

  @Override
  public JsonResult<?> getAiResponse(boolean tts, boolean stream, int productId,
      MultipartFile multipartFile, String token) {
    if (!safetyService.controlAuthorizeProduct(token, productId)) {
      return ResultTool.fail(ResultCode.NO_PERMISSION);
    }
    if (productService.findAllById(productId).isEmpty()) {
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    }
    if (multipartFile.isEmpty())
      return ResultTool.fail(ResultCode.PARAM_NOT_COMPLETE);

    String fileName = multipartFile.getOriginalFilename();
    if (fileName == null)
      return ResultTool.fail(ResultCode.PARAM_NOT_COMPLETE);
    String suffixName = fileName.substring(fileName.lastIndexOf(".")); // 后缀名
    if (!suffixName.equals(".amr") && !suffixName.equals(".wav") &&
        !suffixName.equals(".mp3") && !suffixName.equals(".aac") && !suffixName.equals(".3gp")
        && !suffixName.equals(".3gpp"))
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    String filePath = audioPath; // 上传后的路径
    fileName = UUID.randomUUID() + suffixName; // 新文件名
    JSONObject aiResponse = new JSONObject();
    try {
      MyFileUtil.uploadFile(multipartFile.getBytes(), filePath, fileName);
      // String result = DashScopeVoice
      // .simpleMultiModalConversationCall(audioTempUrl + prefix_url + "/" + fileName);
      String result = audio2Text.getText(audioTempUrl + prefix_url + "/" + fileName);
      log.info(audioTempUrl + prefix_url + "/" + fileName);
      log.info(result);
      String chatId = "chatProduct" + productId;
      String answer = router.response(result, chatId, productId);
      aiResponse.put("text", answer);
      if (tts) {
        var audio = Text2audio.synthesizeAndSaveAudio(answer).array();
        audio = AudioUtils.VoiceBitChange(audio);
        if (!stream)
          aiResponse.put("audio", Base64.getEncoder().encodeToString(audio));
        else
          text2audio.asyncSynthesizeAndSaveAudio(answer, chatId);
      }
    } catch (Exception e) {
      log.error(e.getMessage());
      return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    } finally {
      try {
        MyFileUtil.deleteFile(filePath + fileName);
      } catch (IOException e) {
        log.error(e.getMessage());
      }
    }
    return ResultTool.success(aiResponse);
  }

  @Override
  public JsonResult<?> getMcpPointUrl(int productId) {
    String token = JwtTokenUtil.createNoExpireToken("mcp" + productId, "mcp_endpoint");
    String url = otaUrl + "/mcp?" + "token=" + token;
    return ResultTool.success(url);
  }

  @Override
  public JsonResult<?> getMcpPointTools(int productId) {
    List<Map<String, Object>> toolList = new ArrayList<>();
    if (redisUtil.hasKey(McpWebsocket.ENDPOINT_SERVER_NAME + "mcp" + productId)) {
      toolList = (List<Map<String, Object>>) redisUtil
          .get(McpWebsocket.ENDPOINT_SERVER_NAME + "mcp" + productId);
      if (toolList == null) {
        return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
      }
    } else
      ResultTool.fail(ResultCode.PARAM_NOT_VALID);
    return ResultTool.success(toolList);
  }

  @Override
  public String getAiVisionIntent(String question, MultipartFile imageFile) {
    try {
      return getAiVisionIntent(question, imageFile.getOriginalFilename(), imageFile.getContentType(),
          imageFile.getBytes());
    } catch (Exception ignored) {
      return "无可以使用的视觉模型";
    }
  }

  @Override
  public String getAiVisionIntent(String question, String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return buildVisionResponse(false, null, "图片地址不能为空");
    }
    try {
      URLConnection connection = new URL(imageUrl).openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      long contentLength = connection.getContentLengthLong();
      if (contentLength > 4 * 1024 * 1024) {
        return buildVisionResponse(false, null, "图片大小超过限制");
      }
      try (var inputStream = connection.getInputStream()) {
        return getAiVisionIntent(question, imageUrl, connection.getContentType(), inputStream.readAllBytes());
      }
    } catch (Exception e) {
      log.warn("图片下载失败, url={}, error={}", imageUrl, e.getMessage());
      return buildVisionResponse(false, null, "图片下载失败");
    }
  }

  private String getAiVisionIntent(String question, String imageSource, String contentType, byte[] imageData) {
    try {
      String normalizedContentType = normalizeImageContentType(contentType, imageSource);
      if (normalizedContentType == null || !normalizedContentType.startsWith("image/")) {
        return buildVisionResponse(false, null, "请上传图片文件");
      }

      List<String> supportedTypes = Arrays.asList("image/jpeg", "image/png", "image/webp");
      if (!supportedTypes.contains(normalizedContentType)) {
        return buildVisionResponse(false, null, "不支持的图片格式");
      }

      long maxSizeBytes = 4 * 1024 * 1024;
      if (imageData == null || imageData.length == 0) {
        return buildVisionResponse(false, null, "图片内容为空");
      }
      if (imageData.length > maxSizeBytes) {
        return buildVisionResponse(false, null, "图片大小超过限制");
      }

      String imageBase64 = Base64.getEncoder().encodeToString(imageData);
      String dataUrl = "data:" + normalizedContentType + ";base64," + imageBase64;
      String answer = LLMFactory.getLLM(visionModel).imageToWord(question, dataUrl);
      return buildVisionResponse(true, answer, null);
    } catch (Exception ignored) {
      return "无可以使用的视觉模型";
    }
  }

  private String normalizeImageContentType(String contentType, String imageSource) {
    String normalizedContentType = contentType;
    if (normalizedContentType != null) {
      normalizedContentType = normalizedContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
      if ("image/jpg".equalsIgnoreCase(normalizedContentType)) {
        normalizedContentType = "image/jpeg";
      }
    }
    if ((normalizedContentType == null || normalizedContentType.isBlank()
        || "application/octet-stream".equals(normalizedContentType))
        && imageSource != null && !imageSource.isBlank()) {
      String lowerSource = imageSource.toLowerCase(Locale.ROOT);
      int queryIndex = lowerSource.indexOf('?');
      if (queryIndex >= 0) {
        lowerSource = lowerSource.substring(0, queryIndex);
      }
      if (lowerSource.endsWith(".jpg") || lowerSource.endsWith(".jpeg")) {
        return "image/jpeg";
      }
      if (lowerSource.endsWith(".png")) {
        return "image/png";
      }
      if (lowerSource.endsWith(".webp")) {
        return "image/webp";
      }
    }
    return normalizedContentType;
  }

  private String buildVisionResponse(boolean success, String text, String message) {
    Map<String, Object> result = new HashMap<>();
    result.put("success", success);
    if (text != null) {
      result.put("text", text);
    }
    if (message != null) {
      result.put("message", message);
    }
    try {
      return new ObjectMapper().writeValueAsString(result);
    } catch (Exception ignored) {
      return "无可以使用的视觉模型";
    }
  }

  @Override
  public void audioTmpGet(String name, HttpServletResponse response) throws IOException {
    ServletOutputStream out = response.getOutputStream();
    try {
      String filePath = audioPath; // 上传后的路径
      File file = new File(filePath + name);
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Disposition", "attachment");
      response.addHeader("Content-Length", "" + file.length());
      response.addHeader("Content-Type", "audio/" + name.substring(name.lastIndexOf(".") + 1));
      out.write(Files.readAllBytes(Paths.get(filePath + name)));
      out.flush();
      out.close();


    } catch (Exception e) {
      log.error(e.getMessage());
      response.setStatus(404);
      out.close();
    }
  }
}
