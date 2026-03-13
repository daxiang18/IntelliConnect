package top.rslly.iot.services;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
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
import top.rslly.iot.param.request.InputMessagePromoteParam;
import top.rslly.iot.param.request.InputMessageRecallParam;
import top.rslly.iot.param.response.AgentLongMemoryResponse;
import top.rslly.iot.param.response.InputMessageRecallItemResponse;
import top.rslly.iot.param.response.InputMessageResponse;
import top.rslly.iot.services.agent.AgentLongMemoryServiceImpl;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.input.UrlContentNormalizer;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
  private UrlContentNormalizer urlContentNormalizer;
  @InjectMocks
  private InputMessageServiceImpl inputMessageService;

  private String token;

  @BeforeEach
  void setUp() {
    JwtTokenUtil jwtTokenUtil = new JwtTokenUtil();
    ReflectionTestUtils.setField(jwtTokenUtil, "secretKey", SECRET);
    jwtTokenUtil.init();
    token = JwtTokenUtil.TOKEN_PREFIX + JwtTokenUtil.createToken("smoke-user", "[ROLE_admin]");
  }

  @Test
  void processMessageShouldIngestAndUpdateStatus() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(1L);
    entity.setCreatedBy("smoke-user");
    entity.setSessionId("session-1");
    entity.setDedupeKey("dedupe-1");
    entity.setNormalizedContent("hello world");
    entity.setRawContent("hello world");
    entity.setStatus("received");

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
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), any(TextSegment.class));
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
    verify(knowledgeChatEmbeddingStore).add(any(Embedding.class), any(TextSegment.class));
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
        Metadata.from(Map.of("sessionId", "session-1", "dedupeKey", "dedupe-1")));

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
  void bridgeWechatImageMessageShouldStoreAttachmentWithoutProcessing() {
    when(inputMessageRepository.findFirstByDedupeKey(any())).thenReturn(Optional.empty());
    when(inputMessageRepository.save(any(InputMessageEntity.class)))
        .thenAnswer(invocation -> {
          InputMessageEntity entity = invocation.getArgument(0);
          entity.setId(6L);
          return entity;
        });

    JsonResult<?> result = inputMessageService.bridgeWechatImageMessage("wx-app", "openid-2",
        "wx-user-name", "https://example.com/image.jpg", "msg-2");

    Assertions.assertTrue(result.getSuccess());
    InputMessageEntity entity = (InputMessageEntity) result.getData();
    Assertions.assertEquals("image", entity.getContentType());
    Assertions.assertEquals("received", entity.getStatus());
    Assertions.assertTrue(entity.getAttachmentsJson().contains("\"name\":\"image\""));
    Assertions.assertTrue(entity.getAttachmentsJson().contains("\"url\":\"https://example.com/image.jpg\""));
    verify(taskExecutor, never()).execute(any(Runnable.class));
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
    entity.setDedupeKey("dedupe-test");
    entity.setSessionId("session-x");
    entity.setStatus("received");
    entity.setReceivedAt(1716000000000L);

    when(inputMessageRepository.findFirstByDedupeKeyAndCreatedBy("dedupe-test", "smoke-user"))
        .thenReturn(Optional.of(entity));

    JsonResult<?> result = inputMessageService.getMessageByDedupeKey("dedupe-test", token);

    Assertions.assertTrue(result.getSuccess());
    InputMessageResponse dto = (InputMessageResponse) result.getData();
    Assertions.assertEquals(10L, dto.getId());
    Assertions.assertEquals("dedupe-test", dto.getDedupeKey());
    Assertions.assertEquals("session-x", dto.getSessionId());
    Assertions.assertEquals("received", dto.getStatus());
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
    Assertions.assertEquals(12L, dtos.get(1).getId());
    Assertions.assertEquals("received", dtos.get(1).getStatus());
  }
}
