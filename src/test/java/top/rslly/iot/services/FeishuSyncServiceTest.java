package top.rslly.iot.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.rslly.iot.models.InputMessageEntity;
import top.rslly.iot.services.feishu.FeishuOpenApiClient;
import top.rslly.iot.utility.properties.FeishuProperty;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuSyncServiceTest {
  @Mock
  private FeishuOpenApiClient feishuOpenApiClient;

  private FeishuProperty feishuProperty;
  private FeishuSyncService feishuSyncService;

  @BeforeEach
  void setUp() {
    feishuProperty = new FeishuProperty();
    feishuProperty.setEnabled(true);
    feishuProperty.setAppId("cli-app");
    feishuProperty.setAppSecret("cli-secret");
    feishuProperty.setTitlePrefix("IntelliConnect");
    feishuSyncService = new FeishuSyncService(feishuProperty, feishuOpenApiClient);
  }

  @Test
  void syncMessageShouldCreateDocReference() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(1L);
    entity.setNormalizedContent("# 今日计划\n修复登录问题");
    entity.setRawContent("修复登录问题");

    when(feishuOpenApiClient.getTenantAccessToken("cli-app", "cli-secret")).thenReturn("token-1");
    when(feishuOpenApiClient.createDocument("token-1", "IntelliConnect 今日计划", null))
        .thenReturn(new FeishuOpenApiClient.FeishuDocument("docx-1", "IntelliConnect 今日计划"));

    FeishuSyncService.FeishuSyncResult result = feishuSyncService.syncMessage(entity, "# 今日计划\n修复登录问题");

    Assertions.assertTrue(result.syncedAt() > 0L);
    Assertions.assertEquals("doc", result.reference().get("mode"));
    Assertions.assertEquals("docx-1", result.reference().get("documentId"));
    Assertions.assertEquals("IntelliConnect 今日计划", result.reference().get("title"));
    verify(feishuOpenApiClient).appendDocumentContent("token-1", "docx-1", "# 今日计划\n修复登录问题");
  }

  @Test
  void syncMessageShouldCreateWikiNodeWhenConfigured() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(2L);
    entity.setNormalizedContent("会议纪要");
    entity.setRawContent("会议纪要");

    feishuProperty.setMode("wiki");
    feishuProperty.setWikiSpaceId("space-1");
    feishuProperty.setWikiParentNodeToken("parent-1");
    when(feishuOpenApiClient.getTenantAccessToken("cli-app", "cli-secret")).thenReturn("token-2");
    when(feishuOpenApiClient.createDocument("token-2", "IntelliConnect 会议纪要", null))
        .thenReturn(new FeishuOpenApiClient.FeishuDocument("docx-2", "IntelliConnect 会议纪要"));
    when(feishuOpenApiClient.createWikiNode("token-2", "space-1", "parent-1",
        "IntelliConnect 会议纪要", "docx-2"))
            .thenReturn(new FeishuOpenApiClient.FeishuWikiNode("space-1", "wiki-2",
                "IntelliConnect 会议纪要"));

    FeishuSyncService.FeishuSyncResult result = feishuSyncService.syncMessage(entity, "会议纪要");

    Assertions.assertEquals("wiki", result.reference().get("mode"));
    Assertions.assertEquals("docx-2", result.reference().get("documentId"));
    Assertions.assertEquals("wiki-2", result.reference().get("wikiNodeToken"));
    Assertions.assertEquals("space-1", result.reference().get("wikiSpaceId"));
  }

  @Test
  void syncMessageShouldRejectMissingCredentials() {
    InputMessageEntity entity = new InputMessageEntity();
    entity.setId(3L);
    entity.setNormalizedContent("同步失败");

    feishuProperty.setAppSecret(null);

    IllegalStateException exception =
        Assertions.assertThrows(IllegalStateException.class, () -> feishuSyncService.syncMessage(entity, "同步失败"));

    Assertions.assertEquals("Feishu appId/appSecret not configured", exception.getMessage());
  }
}
