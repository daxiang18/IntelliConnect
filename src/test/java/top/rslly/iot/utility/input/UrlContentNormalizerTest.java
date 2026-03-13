package top.rslly.iot.utility.input;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UrlContentNormalizerTest {
  @Test
  void normalizeHtmlShouldExtractMarkdownLikeContent() {
    String html = """
        <html>
          <head>
            <title>示例页面</title>
            <meta name="description" content="这是一段摘要" />
          </head>
          <body>
            <article>
              <h1>正文标题</h1>
              <p>第一段内容。</p>
              <p>第二段内容。</p>
            </article>
          </body>
        </html>
        """;

    var result = UrlContentNormalizer.normalizeHtml("https://example.com/article", html);

    Assertions.assertTrue(result.success());
    Assertions.assertTrue(result.normalizedContent().contains("# 示例页面"));
    Assertions.assertTrue(result.normalizedContent().contains("- 来源：https://example.com/article"));
    Assertions.assertTrue(result.normalizedContent().contains("- 摘要：这是一段摘要"));
    Assertions.assertTrue(result.normalizedContent().contains("第一段内容。"));
    Assertions.assertTrue(result.normalizedContent().contains("第二段内容。"));
  }

  @Test
  void buildFailureContentShouldExposeReason() {
    String failure = UrlContentNormalizer.buildFailureContent("https://example.com/article", "HTTP 状态码 500");

    Assertions.assertTrue(failure.contains("# URL 内容抓取失败"));
    Assertions.assertTrue(failure.contains("- 来源：https://example.com/article"));
    Assertions.assertTrue(failure.contains("- 原因：HTTP 状态码 500"));
  }
}
