package top.rslly.iot.services.feishu;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.rslly.iot.utility.properties.FeishuProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class HttpFeishuOpenApiClientTest {
  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

  private FeishuProperty feishuProperty;
  private List<Long> retrySleeps;

  @BeforeEach
  void setUp() {
    feishuProperty = new FeishuProperty();
    feishuProperty.setApiBaseUrl("https://open.feishu.cn/open-apis");
    retrySleeps = new ArrayList<>();
  }

  @Test
  void getTenantAccessTokenShouldRetryOnRateLimit() {
    SequenceCallFactory callFactory = new SequenceCallFactory(
        response(429, "{\"msg\":\"rate limited\"}"),
        response(200, "{\"code\":0,\"data\":{\"tenant_access_token\":\"token-1\"}}"));
    HttpFeishuOpenApiClient client =
        new HttpFeishuOpenApiClient(feishuProperty, callFactory, delayMillis -> retrySleeps.add(delayMillis));

    String token = client.getTenantAccessToken("app-id", "app-secret");

    Assertions.assertEquals("token-1", token);
    Assertions.assertEquals(List.of(200L), retrySleeps);
    Assertions.assertEquals(2, callFactory.callCount());
  }

  @Test
  void createDocumentShouldRetryOnNetworkFailure() {
    SequenceCallFactory callFactory = new SequenceCallFactory(
        networkFailure(new IOException("connection reset")),
        response(200, "{\"code\":0,\"data\":{\"document\":{\"document_id\":\"docx-1\",\"title\":\"Title\"}}}"));
    HttpFeishuOpenApiClient client =
        new HttpFeishuOpenApiClient(feishuProperty, callFactory, delayMillis -> retrySleeps.add(delayMillis));

    FeishuOpenApiClient.FeishuDocument document = client.createDocument("tenant-token", "Title", null);

    Assertions.assertEquals("docx-1", document.documentId());
    Assertions.assertEquals("Title", document.title());
    Assertions.assertEquals(List.of(200L), retrySleeps);
    Assertions.assertEquals(2, callFactory.callCount());
  }

  @Test
  void getTenantAccessTokenShouldRetryTwiceThenFailOnServerError() {
    SequenceCallFactory callFactory = new SequenceCallFactory(
        response(503, "{\"msg\":\"server busy\"}"),
        response(502, "{\"msg\":\"bad gateway\"}"),
        response(500, "{\"msg\":\"still failing\"}"));
    HttpFeishuOpenApiClient client =
        new HttpFeishuOpenApiClient(feishuProperty, callFactory, delayMillis -> retrySleeps.add(delayMillis));

    IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class,
            () -> client.getTenantAccessToken("app-id", "app-secret"));

    Assertions.assertEquals("Feishu get tenant access token failed with httpStatus=500, body={\"msg\":\"still failing\"}",
        exception.getMessage());
    Assertions.assertEquals(List.of(200L, 400L), retrySleeps);
    Assertions.assertEquals(3, callFactory.callCount());
  }

  @Test
  void getTenantAccessTokenShouldNotRetryOnBadRequest() {
    SequenceCallFactory callFactory = new SequenceCallFactory(response(400, "{\"msg\":\"bad request\"}"));
    HttpFeishuOpenApiClient client =
        new HttpFeishuOpenApiClient(feishuProperty, callFactory, delayMillis -> retrySleeps.add(delayMillis));

    IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class,
            () -> client.getTenantAccessToken("app-id", "app-secret"));

    Assertions.assertEquals("Feishu get tenant access token failed with httpStatus=400, body={\"msg\":\"bad request\"}",
        exception.getMessage());
    Assertions.assertTrue(retrySleeps.isEmpty());
    Assertions.assertEquals(1, callFactory.callCount());
  }

  private static CallBehavior response(int statusCode, String responseBody) {
    return request -> new Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message("status-" + statusCode)
        .body(ResponseBody.create(responseBody, JSON_MEDIA_TYPE))
        .build();
  }

  private static CallBehavior networkFailure(IOException exception) {
    return request -> {
      throw exception;
    };
  }

  private interface CallBehavior {
    Response execute(Request request) throws IOException;
  }

  private static final class SequenceCallFactory implements Call.Factory {
    private final List<CallBehavior> behaviors;
    private final AtomicInteger callCount = new AtomicInteger();

    private SequenceCallFactory(CallBehavior... behaviors) {
      this.behaviors = Arrays.asList(behaviors);
    }

    @Override
    public Call newCall(Request request) {
      int index = callCount.getAndIncrement();
      if (index >= behaviors.size()) {
        throw new AssertionError("No behavior configured for request " + (index + 1));
      }
      return new FakeCall(request, behaviors.get(index));
    }

    int callCount() {
      return callCount.get();
    }
  }

  private static final class FakeCall implements Call {
    private final Request request;
    private final CallBehavior behavior;
    private boolean executed;
    private boolean canceled;

    private FakeCall(Request request, CallBehavior behavior) {
      this.request = request;
      this.behavior = behavior;
    }

    @Override
    public Request request() {
      return request;
    }

    @Override
    public Response execute() throws IOException {
      executed = true;
      if (canceled) {
        throw new IOException("canceled");
      }
      return behavior.execute(request);
    }

    @Override
    public void enqueue(okhttp3.Callback responseCallback) {
      throw new UnsupportedOperationException("Synchronous only");
    }

    @Override
    public void cancel() {
      canceled = true;
    }

    @Override
    public boolean isExecuted() {
      return executed;
    }

    @Override
    public boolean isCanceled() {
      return canceled;
    }

    @Override
    public Timeout timeout() {
      return Timeout.NONE;
    }

    @Override
    public Call clone() {
      return new FakeCall(request, behavior);
    }
  }
}
