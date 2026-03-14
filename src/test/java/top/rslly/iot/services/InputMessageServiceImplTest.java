package top.rslly.iot.services;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.validation.Validation;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import top.rslly.iot.dao.InputMessageRepository;
import top.rslly.iot.models.InputMessageEntity;
import top.rslly.iot.param.request.AgentLongMemory;
import top.rslly.iot.param.request.InputAttachmentParam;
import top.rslly.iot.param.request.InputMessageCreateParam;
import top.rslly.iot.param.request.InputMessagePromoteParam;
import top.rslly.iot.param.request.InputMessageRecallParam;
import top.rslly.iot.param.response.AgentLongMemoryResponse;
import top.rslly.iot.param.response.InputMessageRecallItemResponse;
import top.rslly.iot.param.response.InputMessageResponse;
import top.rslly.iot.services.agent.AiService;
import top.rslly.iot.services.agent.AgentLongMemoryServiceImpl;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.ai.voice.ASR.AsrService;
import top.rslly.iot.utility.ai.voice.ASR.AsrServiceFactory;
import top.rslly.iot.utility.input.InputContentAutoTagger;
import top.rslly.iot.utility.input.UrlContentNormalizer;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InputMessageServiceImplTest {
  private static final String SECRET = "01234567890123456789012345678901";

  @Mock
  private InputMessageRepository inputMessageRepository;
  @Mock
  private EmbeddingModel embeddingModel;
  @Mock
  private EmbeddingStore<TextSegment> knowledgeChatEmbeddingStore;
  @Mock
  private TaskExecutor taskExecutor;
  @Mock
  private AgentLongMemoryServiceImpl agentLongMemoryService;
  @Mock
  private AiService aiService;
  @Mock
  private UrlContentNormalizer urlContentNormalizer;
  @Mock
  private FeishuSyncService feishuSyncService;
  @Mock
  private AsrServiceFactory asrServiceFactory;
  @InjectMocks
  private InputMessageServiceImpl inputMessageService;

  private String token;

  @BeforeEach
  void setUp() {
    JwtTokenUtil jwtTokenUtil = new JwtTokenUtil();
    ReflectionTestUtils.setField(jwtTokenUtil, "secretKey", SECRET);
    jwtTokenUtil.init();
    token = JwtTokenUtil.TOKEN_PREFIX + JwtTokenUtil.createToken("smoke-user", "[ROLE_admin]");
    ReflectionTestUtils.setField(inputMessageService, "inputContentAutoTagger", new InputContentAutoTagger());
    ReflectionTestUtils.setField(inputMessageService, "validator",
        Validation.buildDefaultValidatorFactory().getValidator());
  }

  @Test
  void processMessageShouldIngestAndUpdateStatus() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(1L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-1");
    entity.setDedupeKey("dedupe-1");
    entity.setContentType("text");
    entity.setNormalizedContent("明天修复登录 bug");
    entity.setRawContent("明天修复登录 bug");
    entity.setStatus("received");
    entity.setSyncTargets("github");
    entity.setSyncStatus("pending");

    when(inputMessageRepository.findById(1L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(1L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    ArgumentCaptor<TextSegment> segmentCaptor = ArgumentCaptor.forClass(TextSegment.class);
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), segmentCaptor.capture());
    Assertions.assertEquals("task", segmentCaptor.getValue().metadata().getString("contentCategory"));
    Assertions.assertEquals("text,task,schedule,bugfix",
        segmentCaptor.getValue().metadata().getString("contentTags"));
    Assertions.assertEquals("github", segmentCaptor.getValue().metadata().getString("syncTargets"));
    Assertions.assertEquals("pending", segmentCaptor.getValue().metadata().getString("syncStatus"));
  }

  @Test
  void processMessageShouldSyncToFeishuAndUpdateSyncState() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(13L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-feishu");
    entity.setDedupeKey("dedupe-feishu");
    entity.setContentType("text");
    entity.setNormalizedContent("同步到飞书文档");
    entity.setRawContent("同步到飞书文档");
    entity.setStatus("received");
    entity.setSyncTargets("feishu");
    entity.setSyncStatus("pending");

    when(inputMessageRepository.findById(13L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    when(feishuSyncService.syncMessage(any(InputMessageEntity.class), any(String.class)))
        .thenReturn(new FeishuSyncService.FeishuSyncResult(1716000002000L,
            Map.of("mode", "doc", "documentId", "docx-123")));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(13L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals("synced", entity.getSyncStatus());
    Assertions.assertEquals(Long.valueOf(1716000002000L), entity.getSyncedAt());
    Assertions.assertNotNull(entity.getExternalReferencesJson());
    Assertions.assertTrue(entity.getExternalReferencesJson().contains("\"documentId\":\"docx-123\""));
    Assertions.assertTrue(entity.getExternalReferencesJson().contains("\"status\":\"synced\""));
    verify(feishuSyncService).syncMessage(entity, "同步到飞书文档");
  }

  @Test
  void processMessageShouldKeepPendingWhenOtherTargetsRemainUnsynced() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(14L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-multi-sync");
    entity.setDedupeKey("dedupe-multi-sync");
    entity.setContentType("text");
    entity.setNormalizedContent("同步到多个目标");
    entity.setRawContent("同步到多个目标");
    entity.setStatus("received");
    entity.setSyncTargets("feishu,github");
    entity.setSyncStatus("pending");

    when(inputMessageRepository.findById(14L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    when(feishuSyncService.syncMessage(any(InputMessageEntity.class), any(String.class)))
        .thenReturn(new FeishuSyncService.FeishuSyncResult(1716000003000L,
            Map.of("mode", "doc", "documentId", "docx-456")));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(14L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals("pending", entity.getSyncStatus());
    Assertions.assertEquals(Long.valueOf(1716000003000L), entity.getSyncedAt());
    Assertions.assertTrue(entity.getExternalReferencesJson().contains("\"documentId\":\"docx-456\""));
    Assertions.assertTrue(entity.getExternalReferencesJson().contains("\"status\":\"synced\""));
  }

  @Test
  void processMessageShouldKeepIngestedWhenFeishuSyncFails() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(15L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-feishu-fail");
    entity.setDedupeKey("dedupe-feishu-fail");
    entity.setContentType("text");
    entity.setNormalizedContent("同步到飞书失败");
    entity.setRawContent("同步到飞书失败");
    entity.setStatus("received");
    entity.setSyncTargets("feishu");
    entity.setSyncStatus("pending");

    when(inputMessageRepository.findById(15L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    when(feishuSyncService.syncMessage(any(InputMessageEntity.class), any(String.class)))
        .thenThrow(new IllegalStateException("Feishu appId/appSecret not configured"));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(15L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals("failed", entity.getSyncStatus());
    Assertions.assertNull(entity.getSyncedAt());
    Assertions.assertNotNull(entity.getExternalReferencesJson());
    Assertions.assertTrue(entity.getExternalReferencesJson().contains("\"errorMessage\":\"Feishu appId/appSecret not configured\""));
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), any(TextSegment.class));
  }

  @Test
  void createMessageShouldNormalizeFeishuSyncTargetAndSetPendingStatus() {
    InputMessageCreateParam param = new InputMessageCreateParam();
    param.setSourceType("manual");
    param.setSourceAccountId("acc-1");
    param.setSessionId("session-create");
    param.setSenderId("sender-1");
    param.setContentType("text");
    param.setRawContent("准备同步到多个平台");
    param.setNormalizedContent("准备同步到多个平台");
    param.setDedupeKey("dedupe-create");
    param.setSyncTargets("Feishu, feishu");

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-create")).thenReturn(Optional.empty());
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> {
          InputMessageEntity entity = invocation.getArgument(0);
          entity.setId(20L);
          return entity;
        });

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertTrue(result.getSuccess());
    InputMessageEntity entity = (InputMessageEntity) result.getData();
    Assertions.assertEquals("feishu", entity.getSyncTargets());
    Assertions.assertEquals("pending", entity.getSyncStatus());
    Assertions.assertNull(entity.getSyncedAt());
    Assertions.assertNull(entity.getExternalReferencesJson());
  }

  @Test
  void createMessageShouldRejectGithubSyncTarget() {
    InputMessageCreateParam param = new InputMessageCreateParam();
    param.setSourceType("manual");
    param.setSessionId("session-create");
    param.setContentType("text");
    param.setRawContent("准备同步到 GitHub");
    param.setDedupeKey("dedupe-github-sync");
    param.setSyncTargets("github");

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-github-sync")).thenReturn(Optional.empty());

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void createMessageShouldRejectUnsupportedSyncTarget() {
    InputMessageCreateParam param = new InputMessageCreateParam();
    param.setSourceType("manual");
    param.setSessionId("session-create");
    param.setContentType("text");
    param.setRawContent("准备同步到未知平台");
    param.setDedupeKey("dedupe-invalid-sync");
    param.setSyncTargets("notion");

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-invalid-sync")).thenReturn(Optional.empty());

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void createMessageShouldRejectNullPayload() {
    JsonResult<?> result = inputMessageService.createMessage(null, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).findFirstByDedupeKey(any());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void createMessageShouldRejectTooManyAttachments() {
    InputMessageCreateParam param = buildValidCreateParam("dedupe-too-many-attachments");
    param.setAttachments(buildAttachments(InputMessageCreateParam.MAX_ATTACHMENTS + 1));

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-too-many-attachments")).thenReturn(Optional.empty());

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void createMessageShouldRejectMalformedAttachmentUrl() {
    InputMessageCreateParam param = buildValidCreateParam("dedupe-malformed-attachment-url");
    param.setAttachments(List.of(buildAttachment("ftp://example.com/image.jpg", "image/jpeg", 1024L)));

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-malformed-attachment-url")).thenReturn(Optional.empty());

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void createMessageShouldRejectMalformedAttachmentContentType() {
    InputMessageCreateParam param = buildValidCreateParam("dedupe-malformed-attachment-type");
    param.setAttachments(List.of(buildAttachment("https://example.com/image.jpg", "image jpeg", 1024L)));

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-malformed-attachment-type"))
        .thenReturn(Optional.empty());

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void createMessageShouldRejectNegativeAttachmentSize() {
    InputMessageCreateParam param = buildValidCreateParam("dedupe-negative-attachment-size");
    param.setAttachments(List.of(buildAttachment("https://example.com/image.jpg", "image/jpeg", -1L)));

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-negative-attachment-size"))
        .thenReturn(Optional.empty());

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void createMessageShouldRejectOversizedAttachment() {
    InputMessageCreateParam param = buildValidCreateParam("dedupe-oversized-attachment");
    param.setAttachments(List.of(buildAttachment("https://example.com/image.jpg", "image/jpeg",
        InputAttachmentParam.MAX_SIZE_BYTES + 1)));

    when(inputMessageRepository.findFirstByDedupeKey("dedupe-oversized-attachment")).thenReturn(Optional.empty());

    JsonResult<?> result = inputMessageService.createMessage(param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).save(any(InputMessageEntity.class));
  }

  @Test
  void processUrlMessageShouldNormalizeUrlBeforeIngest() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(11L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-url");
    entity.setDedupeKey("dedupe-url");
    entity.setContentType("url");
    entity.setRawContent("https://example.com/article");
    entity.setNormalizedContent("[链接] Example article https://example.com/article");
    entity.setStatus("received");

    when(inputMessageRepository.findById(11L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(urlContentNormalizer.normalize("https://example.com/article"))
        .thenReturn(new UrlContentNormalizer.UrlNormalizedResult(true,
            "# Example Article\n\n- 来源：https://example.com/article\n\n## 正文\n正文内容",
            "Example Article", "", null));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(11L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertTrue(entity.getNormalizedContent().contains("# Example Article"));
    verify(urlContentNormalizer).normalize("https://example.com/article");
    ArgumentCaptor<TextSegment> segmentCaptor = ArgumentCaptor.forClass(TextSegment.class);
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), segmentCaptor.capture());
    Assertions.assertEquals("reference", segmentCaptor.getValue().metadata().getString("contentCategory"));
    Assertions.assertEquals("url,example.com,reference",
        segmentCaptor.getValue().metadata().getString("contentTags"));
  }

  @Test
  void processMessageShouldTrackProcessingStartedAtWhileQueued() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(19L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-processing");
    entity.setDedupeKey("dedupe-processing");
    entity.setContentType("text");
    entity.setNormalizedContent("排查 processing 卡住");
    entity.setRawContent("排查 processing 卡住");
    entity.setStatus("received");

    AtomicReference<Runnable> queuedTask = new AtomicReference<>();
    when(inputMessageRepository.findById(19L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      queuedTask.set(invocation.getArgument(0));
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(19L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("processing", entity.getStatus());
    Assertions.assertNotNull(entity.getProcessingStartedAt());
    Assertions.assertTrue(result.getData() instanceof InputMessageEntity);
    InputMessageEntity queuedEntity = (InputMessageEntity) result.getData();
    Assertions.assertEquals(entity.getProcessingStartedAt(), queuedEntity.getProcessingStartedAt());
    Assertions.assertNotNull(queuedTask.get());

    queuedTask.get().run();

    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertNull(entity.getProcessingStartedAt());
  }

  @Test
  void processMessageShouldRejectOtherUsers() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(2L);
    entity.setCreatedBy("another-user");
    entity.setStatus("received");

    when(inputMessageRepository.findById(2L)).thenReturn(Optional.of(entity));

    JsonResult<?> result = inputMessageService.processMessage(2L, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.NO_PERMISSION.getCode(), result.getErrorCode());
    verify(taskExecutor, never()).execute(ArgumentMatchers.any(Runnable.class));
  }

  @Test
  void retryMessageShouldQueueFailedMessageAgain() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(16L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-retry");
    entity.setDedupeKey("dedupe-retry");
    entity.setContentType("text");
    entity.setNormalizedContent("重新处理失败消息");
    entity.setRawContent("重新处理失败消息");
    entity.setStatus("failed");
    entity.setSyncStatus("not_requested");

    when(inputMessageRepository.findById(16L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.retryMessage(16L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertTrue(result.getData() instanceof InputMessageResponse);
    InputMessageResponse response = (InputMessageResponse) result.getData();
    Assertions.assertEquals(16L, response.getId());
    Assertions.assertEquals("ingested", response.getStatus());
    Assertions.assertEquals("dedupe-retry", response.getDedupeKey());
    Assertions.assertEquals("ingested", entity.getStatus());
    verify(taskExecutor).execute(any(Runnable.class));
  }

  @Test
  void retryMessageShouldRejectSyncOnlyFailureState() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(17L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-sync-fail");
    entity.setDedupeKey("dedupe-sync-fail");
    entity.setContentType("text");
    entity.setNormalizedContent("只同步失败");
    entity.setRawContent("只同步失败");
    entity.setStatus("ingested");
    entity.setSyncTargets("feishu");
    entity.setSyncStatus("failed");
    entity.setExternalReferencesJson("{\"feishu\":{\"status\":\"failed\"}}");

    when(inputMessageRepository.findById(17L)).thenReturn(Optional.of(entity));

    JsonResult<?> result = inputMessageService.retryMessage(17L, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(taskExecutor, never()).execute(any(Runnable.class));
  }

  @Test
  void retryMessageShouldRejectOtherUsers() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(18L);
    entity.setCreatedBy("another-user");
    entity.setStatus("failed");

    when(inputMessageRepository.findById(18L)).thenReturn(Optional.of(entity));

    JsonResult<?> result = inputMessageService.retryMessage(18L, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.NO_PERMISSION.getCode(), result.getErrorCode());
    verify(taskExecutor, never()).execute(any(Runnable.class));
  }

  @Test
  void recallMessagesShouldReturnScopedMatches() {
    InputMessageRecallParam param = new InputMessageRecallParam();
    param.setQuery("hello");
    param.setSessionId("session-1");

    @SuppressWarnings("unchecked")
    EmbeddingSearchResult<TextSegment> searchResult = (EmbeddingSearchResult<TextSegment>) org.mockito.Mockito.mock(
        EmbeddingSearchResult.class);
    @SuppressWarnings("unchecked")
    EmbeddingMatch<TextSegment> match = (EmbeddingMatch<TextSegment>) org.mockito.Mockito.mock(
        EmbeddingMatch.class);
    TextSegment segment = TextSegment.from("hello world",
        Metadata.from(Map.of(
            "sessionId", "session-1",
            "dedupeKey", "dedupe-1",
            "contentCategory", "knowledge",
            "contentTags", "text,knowledge")));

    when(embeddingModel.embed("hello"))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    when(knowledgeChatEmbeddingStore.search(any())).thenReturn(searchResult);
    when(searchResult.matches()).thenReturn(List.of(match));
    when(match.embedded()).thenReturn(segment);
    when(match.score()).thenReturn(0.91);

    JsonResult<?> result = inputMessageService.recallMessages(param, token);

    Assertions.assertTrue(result.getSuccess());
    @SuppressWarnings("unchecked")
    List<InputMessageRecallItemResponse> data = (List<InputMessageRecallItemResponse>) result.getData();
    Assertions.assertEquals(1, data.size());
    Assertions.assertEquals("hello world", data.get(0).getText());
    Assertions.assertEquals("session-1", data.get(0).getSessionId());
    Assertions.assertEquals("dedupe-1", data.get(0).getDedupeKey());
    Assertions.assertEquals("knowledge", data.get(0).getCategory());
    Assertions.assertEquals(List.of("text", "knowledge"), data.get(0).getTags());
    verify(knowledgeChatEmbeddingStore).search(any());
  }

  @Test
  void recallMessagesShouldRejectInvalidToken() {
    InputMessageRecallParam param = new InputMessageRecallParam();
    param.setQuery("hello");

    JsonResult<?> result = inputMessageService.recallMessages(param, "Bearer bad-token");

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(knowledgeChatEmbeddingStore, never()).search(any());
  }

  @Test
  void promoteMessageToLongMemoryShouldUseIngestedContent() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(3L);
    entity.setCreatedBy("smoke-user");
    entity.setStatus("ingested");
    entity.setNormalizedContent("normalized content");
    entity.setRawContent("raw content");

    InputMessagePromoteParam param = new InputMessagePromoteParam();
    param.setProductId(9);
    param.setMemoryKey("input:session-1");
    param.setDescription("from input");

    when(inputMessageRepository.findById(3L)).thenReturn(Optional.of(entity));
    when(agentLongMemoryService.postLongMemory(any(AgentLongMemory.class)))
        .thenAnswer(invocation -> {
          AgentLongMemory request = invocation.getArgument(0);
          AgentLongMemoryResponse response = new AgentLongMemoryResponse();
          response.setProductId(request.getProductId());
          response.setMemoryKey(request.getMemoryKey());
          response.setDescription(request.getDescription());
          response.setMemoryValue(request.getMemoryValue());
          return new JsonResult<>(true, response);
        });

    JsonResult<?> result = inputMessageService.promoteMessageToLongMemory(3L, param, token);

    Assertions.assertTrue(result.getSuccess());
    AgentLongMemoryResponse promoted = (AgentLongMemoryResponse) result.getData();
    Assertions.assertEquals(9, promoted.getProductId());
    Assertions.assertEquals("input:session-1", promoted.getMemoryKey());
    Assertions.assertEquals("normalized content", promoted.getMemoryValue());
    verify(agentLongMemoryService).postLongMemory(any(AgentLongMemory.class));
  }

  @Test
  void promoteMessageToLongMemoryShouldRejectNonIngestedMessage() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(4L);
    entity.setCreatedBy("smoke-user");
    entity.setStatus("processing");

    InputMessagePromoteParam param = new InputMessagePromoteParam();
    param.setProductId(9);
    param.setMemoryKey("input:session-2");
    param.setDescription("from input");

    when(inputMessageRepository.findById(4L)).thenReturn(Optional.of(entity));

    JsonResult<?> result = inputMessageService.promoteMessageToLongMemory(4L, param, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(agentLongMemoryService, never()).postLongMemory(any(AgentLongMemory.class));
  }

  @Test
  void bridgeWechatTextMessageShouldCreateAndProcessMessage() {
    AtomicReference<InputMessageEntity> savedRef = new AtomicReference<>();
    when(inputMessageRepository.findFirstByDedupeKey(any())).thenReturn(Optional.empty());
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> {
          InputMessageEntity entity = invocation.getArgument(0);
          if (entity.getId() == 0L) {
            entity.setId(5L);
          }
          savedRef.set(entity);
          return entity;
        });
    when(inputMessageRepository.findById(5L)).thenAnswer(invocation -> Optional.of(savedRef.get()));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.bridgeWechatTextMessage("wx-app", "openid-1",
        "wx-user-name", "hello from wechat", "msg-1");

    Assertions.assertTrue(result.getSuccess());
    InputMessageEntity entity = (InputMessageEntity) result.getData();
    Assertions.assertEquals("wx-user-name", entity.getCreatedBy());
    Assertions.assertEquals("wechat", entity.getSourceType());
    Assertions.assertEquals("wechat:wx-app:openid-1", entity.getSessionId());
    Assertions.assertEquals("wechat:wx-app:openid-1:msg-1", entity.getDedupeKey());
    Assertions.assertEquals("ingested", entity.getStatus());
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), any(TextSegment.class));
  }

  @Test
  void bridgeWechatUrlMessageShouldCreateAndProcessMessage() {
    AtomicReference<InputMessageEntity> savedRef = new AtomicReference<>();
    when(inputMessageRepository.findFirstByDedupeKey(any())).thenReturn(Optional.empty());
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> {
          InputMessageEntity entity = invocation.getArgument(0);
          if (entity.getId() == 0L) {
            entity.setId(8L);
          }
          savedRef.set(entity);
          return entity;
        });
    when(inputMessageRepository.findById(8L)).thenAnswer(invocation -> Optional.of(savedRef.get()));
    when(urlContentNormalizer.normalize("https://example.com/link"))
        .thenReturn(new UrlContentNormalizer.UrlNormalizedResult(true,
            "# Link Title\n\n- 来源：https://example.com/link\n\n## 正文\nLink body",
            "Link Title", "", null));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.bridgeWechatUrlMessage("wx-app", "openid-4",
        "wx-user-name", "https://example.com/link", "Link Title", "Link summary", "msg-4");

    Assertions.assertTrue(result.getSuccess());
    InputMessageEntity entity = (InputMessageEntity) result.getData();
    Assertions.assertEquals("url", entity.getContentType());
    Assertions.assertEquals("https://example.com/link", entity.getRawContent());
    Assertions.assertTrue(entity.getNormalizedContent().contains("# Link Title"));
    Assertions.assertEquals("ingested", entity.getStatus());
    verify(urlContentNormalizer).normalize("https://example.com/link");
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), any(TextSegment.class));
  }

  @Test
  void bridgeWechatImageMessageShouldEnrichAndProcess() {
    AtomicReference<InputMessageEntity> savedRef = new AtomicReference<>();
    when(inputMessageRepository.findFirstByDedupeKey(any())).thenReturn(Optional.empty());
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> {
          InputMessageEntity entity = invocation.getArgument(0);
          if (entity.getId() == 0L) {
            entity.setId(6L);
          }
          savedRef.set(entity);
          return entity;
        });
    when(inputMessageRepository.findById(6L)).thenAnswer(invocation -> Optional.of(savedRef.get()));
    when(aiService.getAiVisionIntent(ArgumentMatchers.anyString(), ArgumentMatchers.eq("https://example.com/image.jpg")))
        .thenReturn("{\"success\":true,\"text\":\"白板上写着本周发布计划\"}");
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.bridgeWechatImageMessage("wx-app", "openid-2",
        "wx-user-name", "https://example.com/image.jpg", "msg-2");

    Assertions.assertTrue(result.getSuccess());
    InputMessageEntity entity = (InputMessageEntity) result.getData();
    Assertions.assertEquals("image", entity.getContentType());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertTrue(entity.getNormalizedContent().contains("本周发布计划"));
    Assertions.assertTrue(entity.getNormalizedContent().contains("https://example.com/image.jpg"));
    Assertions.assertTrue(entity.getAttachmentsJson().contains("\"name\":\"image\""));
    Assertions.assertTrue(entity.getAttachmentsJson().contains("\"url\":\"https://example.com/image.jpg\""));
    ArgumentCaptor<TextSegment> segmentCaptor = ArgumentCaptor.forClass(TextSegment.class);
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), segmentCaptor.capture());
    Assertions.assertTrue(segmentCaptor.getValue().text().contains("本周发布计划"));
    verify(aiService).getAiVisionIntent(ArgumentMatchers.anyString(),
        ArgumentMatchers.eq("https://example.com/image.jpg"));
  }

  @Test
  void processImageMessageShouldFallbackToOriginalContentWhenVisionFails() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(19L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-image");
    entity.setDedupeKey("dedupe-image");
    entity.setContentType("image");
    entity.setRawContent("https://example.com/image.jpg");
    entity.setNormalizedContent("https://example.com/image.jpg");
    entity.setStatus("received");

    when(inputMessageRepository.findById(19L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(aiService.getAiVisionIntent(ArgumentMatchers.anyString(),
        ArgumentMatchers.eq("https://example.com/image.jpg")))
        .thenReturn("{\"success\":false,\"message\":\"图片下载失败\"}");
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(19L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals("https://example.com/image.jpg", entity.getNormalizedContent());
    ArgumentCaptor<TextSegment> segmentCaptor = ArgumentCaptor.forClass(TextSegment.class);
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), segmentCaptor.capture());
    Assertions.assertEquals("https://example.com/image.jpg", segmentCaptor.getValue().text());
  }

  @Test
  void bridgeWechatVoiceMessageShouldStoreAttachmentAndProcess() {
    AtomicReference<InputMessageEntity> savedRef = new AtomicReference<>();
    when(inputMessageRepository.findFirstByDedupeKey(any())).thenReturn(Optional.empty());
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> {
          InputMessageEntity entity = invocation.getArgument(0);
          if (entity.getId() == 0L) {
            entity.setId(7L);
          }
          savedRef.set(entity);
          return entity;
        });
    when(inputMessageRepository.findById(7L)).thenAnswer(invocation -> Optional.of(savedRef.get()));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.bridgeWechatVoiceMessage("wx-app", "openid-3",
        "wx-user-name", "https://example.com/audio.amr", "voice transcript", "msg-3");

    Assertions.assertTrue(result.getSuccess());
    InputMessageEntity entity = (InputMessageEntity) result.getData();
    Assertions.assertEquals("voice", entity.getContentType());
    Assertions.assertEquals("https://example.com/audio.amr", entity.getRawContent());
    Assertions.assertEquals("voice transcript", entity.getNormalizedContent());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertTrue(entity.getAttachmentsJson().contains("\"name\":\"voice\""));
    Assertions.assertTrue(entity.getAttachmentsJson().contains("\"contentType\":\"audio/amr\""));
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), any(TextSegment.class));
  }

  @Test
  void getMessageByDedupeKeyShouldReturnDto() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(10L);
    entity.setCreatedBy("smoke-user");
    entity.setContentType("text");
    entity.setDedupeKey("dedupe-test");
    entity.setSessionId("session-x");
    entity.setNormalizedContent("明天安排验收任务");
    entity.setStatus("received");
    entity.setReceivedAt(1716000000000L);
    entity.setSyncTargets("feishu,github");
    entity.setSyncStatus("pending");
    entity.setSyncedAt(1716000001000L);
    entity.setExternalReferencesJson("{\"github\":{\"issueUrl\":\"https://github.com/demo/issues/1\"}}");

    when(inputMessageRepository.findFirstByDedupeKeyAndCreatedBy("dedupe-test", "smoke-user"))
        .thenReturn(Optional.of(entity));

    JsonResult<?> result = inputMessageService.getMessageByDedupeKey("dedupe-test", token);

    Assertions.assertTrue(result.getSuccess());
    InputMessageResponse dto = (InputMessageResponse) result.getData();
    Assertions.assertEquals(10L, dto.getId());
    Assertions.assertEquals("dedupe-test", dto.getDedupeKey());
    Assertions.assertEquals("session-x", dto.getSessionId());
    Assertions.assertEquals("received", dto.getStatus());
    Assertions.assertEquals("task", dto.getCategory());
    Assertions.assertTrue(dto.getTags().contains("task"));
    Assertions.assertEquals(List.of("feishu", "github"), dto.getSyncTargets());
    Assertions.assertEquals("pending", dto.getSyncStatus());
    Assertions.assertEquals(Long.valueOf(1716000001000L), dto.getSyncedAt());
    Assertions.assertNull(dto.getProcessingStartedAt());
    Assertions.assertNull(dto.getProcessingDurationMs());
    Assertions.assertEquals("{\"github\":{\"issueUrl\":\"https://github.com/demo/issues/1\"}}",
        dto.getExternalReferencesJson());
  }

  @Test
  void getMessageByDedupeKeyShouldExposeProcessingTimingFields() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(24L);
    entity.setCreatedBy("smoke-user");
    entity.setContentType("text");
    entity.setDedupeKey("dedupe-processing-dto");
    entity.setSessionId("session-processing-dto");
    entity.setNormalizedContent("处理仍在进行中");
    entity.setStatus("processing");
    entity.setReceivedAt(System.currentTimeMillis() - 65_000L);
    entity.setProcessingStartedAt(null);

    when(inputMessageRepository.findFirstByDedupeKeyAndCreatedBy("dedupe-processing-dto", "smoke-user"))
        .thenReturn(Optional.of(entity));

    JsonResult<?> result = inputMessageService.getMessageByDedupeKey("dedupe-processing-dto", token);

    Assertions.assertTrue(result.getSuccess());
    InputMessageResponse dto = (InputMessageResponse) result.getData();
    Assertions.assertEquals(Long.valueOf(entity.getReceivedAt()), dto.getProcessingStartedAt());
    Assertions.assertNotNull(dto.getProcessingDurationMs());
    Assertions.assertTrue(dto.getProcessingDurationMs() >= 65_000L);
  }

  @Test
  void getMessagesBySessionIdShouldReturnDtoList() {
    InputMessageEntity first = new InputMessageEntity();
    first.setId(11L);
    first.setCreatedBy("smoke-user");
    first.setSessionId("session-y");
    first.setStatus("ingested");

    InputMessageEntity second = new InputMessageEntity();
    second.setId(12L);
    second.setCreatedBy("smoke-user");
    second.setSessionId("session-y");
    second.setStatus("received");

    when(inputMessageRepository.findAllBySessionIdAndCreatedByOrderByReceivedAtDesc("session-y",
        "smoke-user")).thenReturn(List.of(first, second));

    JsonResult<?> result = inputMessageService.getMessagesBySessionId("session-y", token);

    Assertions.assertTrue(result.getSuccess());
    @SuppressWarnings("unchecked")
    List<InputMessageResponse> dtos = (List<InputMessageResponse>) result.getData();
    Assertions.assertEquals(2, dtos.size());
    Assertions.assertEquals(11L, dtos.get(0).getId());
    Assertions.assertEquals("ingested", dtos.get(0).getStatus());
    Assertions.assertTrue(dtos.get(0).getSyncTargets().isEmpty());
    Assertions.assertEquals("not_requested", dtos.get(0).getSyncStatus());
    Assertions.assertNull(dtos.get(0).getProcessingStartedAt());
    Assertions.assertNull(dtos.get(0).getProcessingDurationMs());
    Assertions.assertEquals(12L, dtos.get(1).getId());
    Assertions.assertEquals("received", dtos.get(1).getStatus());
  }

  @Test
  void getMessagesBySessionIdShouldApplyPaginationWhenRequested() {
    InputMessageEntity first = new InputMessageEntity();
    first.setId(21L);
    first.setCreatedBy("smoke-user");
    first.setSessionId("session-page");
    first.setStatus("ingested");

    InputMessageEntity second = new InputMessageEntity();
    second.setId(22L);
    second.setCreatedBy("smoke-user");
    second.setSessionId("session-page");
    second.setStatus("received");

    when(inputMessageRepository.findAllBySessionIdAndCreatedBy(
        org.mockito.ArgumentMatchers.eq("session-page"),
        org.mockito.ArgumentMatchers.eq("smoke-user"),
        any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(first, second)));

    JsonResult<?> result = inputMessageService.getMessagesBySessionId("session-page", 1, 2, token);

    Assertions.assertTrue(result.getSuccess());
    @SuppressWarnings("unchecked")
    List<InputMessageResponse> dtos = (List<InputMessageResponse>) result.getData();
    Assertions.assertEquals(2, dtos.size());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(inputMessageRepository).findAllBySessionIdAndCreatedBy(
        org.mockito.ArgumentMatchers.eq("session-page"),
        org.mockito.ArgumentMatchers.eq("smoke-user"),
        pageableCaptor.capture());
    Assertions.assertEquals(1, pageableCaptor.getValue().getPageNumber());
    Assertions.assertEquals(2, pageableCaptor.getValue().getPageSize());
  }

  @Test
  void getMessagesBySessionIdShouldRejectInvalidPaginationInput() {
    JsonResult<?> result = inputMessageService.getMessagesBySessionId("session-page", 0, 101, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).findAllBySessionIdAndCreatedBy(
        org.mockito.ArgumentMatchers.eq("session-page"),
        org.mockito.ArgumentMatchers.eq("smoke-user"),
        any(Pageable.class));
  }

  @Test
  void getStaleProcessingMessagesShouldReturnSessionScopedDtos() {
    InputMessageEntity stale = new InputMessageEntity();
    stale.setId(25L);
    stale.setCreatedBy("smoke-user");
    stale.setSessionId("session-stale");
    stale.setStatus("processing");
    stale.setContentType("text");
    stale.setDedupeKey("dedupe-stale");
    stale.setNormalizedContent("这是一个疑似卡住的任务");
    stale.setReceivedAt(1716000003000L);
    stale.setProcessingStartedAt(System.currentTimeMillis() - 31 * 60_000L);
    stale.setSyncTargets("feishu");
    stale.setSyncStatus("pending");

    when(inputMessageRepository.findAllBySessionIdAndCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
        org.mockito.ArgumentMatchers.eq("session-stale"),
        org.mockito.ArgumentMatchers.eq("smoke-user"),
        org.mockito.ArgumentMatchers.eq("processing"),
        org.mockito.ArgumentMatchers.anyLong(),
        any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(stale)));

    JsonResult<?> result = inputMessageService.getStaleProcessingMessages("session-stale", 30, 5, token);

    Assertions.assertTrue(result.getSuccess());
    @SuppressWarnings("unchecked")
    List<InputMessageResponse> dtos = (List<InputMessageResponse>) result.getData();
    Assertions.assertEquals(1, dtos.size());
    Assertions.assertEquals(25L, dtos.get(0).getId());
    Assertions.assertEquals(stale.getProcessingStartedAt(), dtos.get(0).getProcessingStartedAt());
    Assertions.assertNotNull(dtos.get(0).getProcessingDurationMs());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(inputMessageRepository).findAllBySessionIdAndCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
        org.mockito.ArgumentMatchers.eq("session-stale"),
        org.mockito.ArgumentMatchers.eq("smoke-user"),
        org.mockito.ArgumentMatchers.eq("processing"),
        org.mockito.ArgumentMatchers.anyLong(),
        pageableCaptor.capture());
    Assertions.assertEquals(0, pageableCaptor.getValue().getPageNumber());
    Assertions.assertEquals(5, pageableCaptor.getValue().getPageSize());
  }

  @Test
  void getStaleProcessingMessagesShouldRejectInvalidArguments() {
    JsonResult<?> result = inputMessageService.getStaleProcessingMessages("session-stale", 0, 101, token);

    Assertions.assertFalse(result.getSuccess());
    Assertions.assertEquals(ResultCode.PARAM_NOT_VALID.getCode(), result.getErrorCode());
    verify(inputMessageRepository, never()).findAllByCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
        org.mockito.ArgumentMatchers.eq("smoke-user"),
        org.mockito.ArgumentMatchers.eq("processing"),
        org.mockito.ArgumentMatchers.anyLong(),
        any(Pageable.class));
    verify(inputMessageRepository, never()).findAllBySessionIdAndCreatedByAndStatusAndProcessingStartedAtLessThanEqual(
        org.mockito.ArgumentMatchers.eq("session-stale"),
        org.mockito.ArgumentMatchers.eq("smoke-user"),
        org.mockito.ArgumentMatchers.eq("processing"),
        org.mockito.ArgumentMatchers.anyLong(),
        any(Pageable.class));
  }

  @Test
  void processVoiceMessageWithoutTranscriptShouldCallAsrAndIngest() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(30L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-voice-asr");
    entity.setDedupeKey("dedupe-voice-asr");
    entity.setContentType("voice");
    entity.setRawContent("https://example.com/audio.amr");
    entity.setNormalizedContent("https://example.com/audio.amr");
    entity.setStatus("received");

    AsrService mockAsrService = mock(AsrService.class);
    when(asrServiceFactory.getService()).thenReturn(mockAsrService);
    when(mockAsrService.getText("https://example.com/audio.amr")).thenReturn("今天天气很好");
    when(inputMessageRepository.findById(30L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(30L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals("今天天气很好", entity.getNormalizedContent());
    verify(mockAsrService).getText("https://example.com/audio.amr");
    ArgumentCaptor<TextSegment> segmentCaptor = ArgumentCaptor.forClass(TextSegment.class);
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), segmentCaptor.capture());
    Assertions.assertEquals("今天天气很好", segmentCaptor.getValue().text());
    Assertions.assertEquals("voice", segmentCaptor.getValue().metadata().getString("contentType"));
  }

  @Test
  void processVoiceMessageWithExistingTranscriptShouldSkipAsr() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(31L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-voice-skip");
    entity.setDedupeKey("dedupe-voice-skip");
    entity.setContentType("voice");
    entity.setRawContent("https://example.com/audio2.amr");
    entity.setNormalizedContent("帮我打开空调");
    entity.setStatus("received");

    when(inputMessageRepository.findById(31L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(31L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals("帮我打开空调", entity.getNormalizedContent());
    verify(asrServiceFactory, never()).getService();
    ArgumentCaptor<TextSegment> segmentCaptor = ArgumentCaptor.forClass(TextSegment.class);
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), segmentCaptor.capture());
    Assertions.assertEquals("帮我打开空调", segmentCaptor.getValue().text());
  }

  @Test
  void processVoiceMessageWithAsrFailureShouldFallBackToAudioUrl() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(32L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-voice-fail");
    entity.setDedupeKey("dedupe-voice-fail");
    entity.setContentType("voice");
    entity.setRawContent("https://example.com/audio3.amr");
    entity.setNormalizedContent("https://example.com/audio3.amr");
    entity.setStatus("received");

    AsrService mockAsrService = mock(AsrService.class);
    when(asrServiceFactory.getService()).thenReturn(mockAsrService);
    when(mockAsrService.getText(anyString())).thenReturn("");
    when(inputMessageRepository.findById(32L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(32L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals("https://example.com/audio3.amr", entity.getNormalizedContent());
    ArgumentCaptor<TextSegment> segmentCaptor = ArgumentCaptor.forClass(TextSegment.class);
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), segmentCaptor.capture());
    Assertions.assertEquals("https://example.com/audio3.amr", segmentCaptor.getValue().text());
  }

  @Test
  void processMessageShouldMintAttemptTokenAndIncrementCount() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(40L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-attempt");
    entity.setDedupeKey("dedupe-attempt");
    entity.setContentType("text");
    entity.setNormalizedContent("attempt counter test");
    entity.setRawContent("attempt counter test");
    entity.setStatus("received");
    entity.setProcessingAttemptCount(0);

    when(inputMessageRepository.findById(40L)).thenReturn(Optional.of(entity));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    JsonResult<?> result = inputMessageService.processMessage(40L, token);

    Assertions.assertTrue(result.getSuccess());
    Assertions.assertEquals("ingested", entity.getStatus());
    Assertions.assertEquals(1, entity.getProcessingAttemptCount());
    Assertions.assertNull(entity.getProcessingAttemptToken(), "token cleared after finalization");
  }

  @Test
  void processMessageAttemptTokenMismatchShouldSkipStatusFinalization() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(41L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-stale-token");
    entity.setDedupeKey("dedupe-stale-token");
    entity.setContentType("text");
    entity.setNormalizedContent("stale token test");
    entity.setRawContent("stale token test");
    entity.setStatus("received");
    entity.setProcessingAttemptCount(1);
    entity.setProcessingAttemptToken("old-token-xyz");

    AtomicReference<InputMessageEntity> savedRef = new AtomicReference<>(entity);

    when(inputMessageRepository.findById(41L)).thenAnswer(invocation -> Optional.of(savedRef.get()));
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> {
          InputMessageEntity saved = invocation.getArgument(0);
          savedRef.set(saved);
          return saved;
        });
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));

    doAnswer(invocation -> {
      savedRef.get().setProcessingAttemptToken(null);
      savedRef.get().setStatus("received");
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(taskExecutor).execute(any(Runnable.class));

    inputMessageService.processMessage(41L, token);

    Assertions.assertEquals("received", savedRef.get().getStatus());
    Assertions.assertNull(savedRef.get().getProcessingAttemptToken());
  }

  private InputMessageCreateParam buildValidCreateParam(String dedupeKey) {
    InputMessageCreateParam param = new InputMessageCreateParam();
    param.setSourceType("manual");
    param.setSessionId("session-create");
    param.setContentType("image");
    param.setRawContent("https://example.com/image.jpg");
    param.setNormalizedContent("https://example.com/image.jpg");
    param.setDedupeKey(dedupeKey);
    return param;
  }

  private List<InputAttachmentParam> buildAttachments(int count) {
    List<InputAttachmentParam> attachments = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      InputAttachmentParam attachment = buildAttachment("https://example.com/file-" + i + ".jpg",
          "image/jpeg", 1024L);
      attachment.setName("attachment-" + i);
      attachments.add(attachment);
    }
    return attachments;
  }

  private InputAttachmentParam buildAttachment(String url, String contentType, Long size) {
    InputAttachmentParam attachment = new InputAttachmentParam();
    attachment.setName("image");
    attachment.setUrl(url);
    attachment.setContentType(contentType);
    attachment.setSize(size);
    return attachment;
  }
}
