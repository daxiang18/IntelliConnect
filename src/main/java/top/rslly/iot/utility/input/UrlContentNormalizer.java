package top.rslly.iot.utility.input;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import top.rslly.iot.utility.HttpRequestUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UrlContentNormalizer {
  private static final int MAX_TITLE_LENGTH = 200;
  private static final int MAX_DESCRIPTION_LENGTH = 300;
  private static final int MAX_BODY_LENGTH = 7600;
  private static final int MAX_NORMALIZED_LENGTH = 9000;
  private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
  private static final Pattern META_TAG_PATTERN = Pattern.compile("(?is)<meta\\b([^>]*?)>");
  private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
      "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>/]+))");
  private static final Pattern ARTICLE_PATTERN = Pattern.compile("(?is)<article\\b[^>]*>(.*?)</article>");
  private static final Pattern MAIN_PATTERN = Pattern.compile("(?is)<main\\b[^>]*>(.*?)</main>");
  private static final Pattern BODY_PATTERN = Pattern.compile("(?is)<body\\b[^>]*>(.*?)</body>");
  private static final Pattern COMMENT_PATTERN = Pattern.compile("(?is)<!--.*?-->");
  private static final Pattern SCRIPT_PATTERN =
      Pattern.compile("(?is)<(script|style|noscript|svg|canvas|iframe)\\b.*?</\\1>");
  private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
  private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("(?i)^https?://.+");
  private static final Pattern URL_EXTRACT_PATTERN = Pattern.compile("(https?://[^\\s]+)");

  @Autowired
  private HttpRequestUtils httpRequestUtils;

  public UrlNormalizedResult normalize(String url) {
    if (url == null || url.isBlank()) {
      return failure(url, "URL 为空");
    }
    String trimmedUrl = url.trim();
    // 如果整段内容不是纯 URL，尝试从中提取第一个 http/https URL
    if (!URL_SCHEME_PATTERN.matcher(trimmedUrl).matches()) {
      Matcher urlMatcher = URL_EXTRACT_PATTERN.matcher(trimmedUrl);
      if (urlMatcher.find()) {
        trimmedUrl = urlMatcher.group(1);
        log.info("extracted URL from mixed content: {}", trimmedUrl);
      } else {
        return failure(trimmedUrl, "仅支持 http/https URL");
      }
    }

    try (Response response = httpRequestUtils.httpGet(trimmedUrl)) {
      if (response == null) {
        return failure(trimmedUrl, "响应为空");
      }
      if (!response.isSuccessful()) {
        return failure(trimmedUrl, "HTTP 状态码 " + response.code());
      }

      ResponseBody body = response.body();
      if (body == null) {
        return failure(trimmedUrl, "响应体为空");
      }

      String contentType = response.header("Content-Type", "");
      String responseBody = body.string();
      return normalizeFetchedContent(trimmedUrl, responseBody, contentType);
    } catch (IOException e) {
      log.warn("normalize url content failed, url={}", trimmedUrl, e);
      return failure(trimmedUrl, buildExceptionMessage(e));
    }
  }

  public static UrlNormalizedResult normalizeFetchedContent(String url, String responseBody,
      String contentType) {
    if (responseBody == null || responseBody.isBlank()) {
      return failure(url, "响应内容为空");
    }

    if (looksLikeHtml(contentType, responseBody)) {
      return normalizeHtml(url, responseBody);
    }
    if (looksLikePlainText(contentType)) {
      String normalizedText = normalizeText(responseBody);
      if (normalizedText.isBlank()) {
        return failure(url, "未提取到可用正文");
      }
      String title = safeTitleFromUrl(url);
      String normalizedContent = buildMarkdownLikeContent(url, title, "", normalizedText);
      return new UrlNormalizedResult(true, normalizedContent, title, "", null);
    }
    return failure(url, "暂不支持的内容类型: " + defaultString(contentType, "unknown"));
  }

  public static UrlNormalizedResult normalizeHtml(String url, String html) {
    if (html == null || html.isBlank()) {
      return failure(url, "响应内容为空");
    }

    String title = firstNonBlank(
        extractMetaContent(html, "og:title", "twitter:title"),
        extractTagText(TITLE_PATTERN, html),
        safeTitleFromUrl(url));
    String description = firstNonBlank(
        extractMetaContent(html, "description", "og:description", "twitter:description"),
        "");
    String mainHtml = firstNonBlank(
        extractTagText(ARTICLE_PATTERN, html),
        extractTagText(MAIN_PATTERN, html),
        extractTagText(BODY_PATTERN, html),
        html);
    String mainText = normalizeTextFromHtml(mainHtml);

    if (mainText.isBlank()) {
      mainText = description;
    }
    if (mainText.isBlank()) {
      return failure(url, "未提取到可用正文");
    }

    String normalizedContent = buildMarkdownLikeContent(url, title, description, mainText);
    return new UrlNormalizedResult(true, normalizedContent, truncate(title, MAX_TITLE_LENGTH),
        truncate(description, MAX_DESCRIPTION_LENGTH), null);
  }

  public static String buildLinkSummary(String title, String description, String url) {
    String normalizedTitle = normalizeInlineText(title);
    String normalizedDescription = normalizeInlineText(description);
    String normalizedUrl = defaultString(url, "").trim();

    StringBuilder builder = new StringBuilder("[链接]");
    if (!normalizedTitle.isBlank()) {
      builder.append(' ').append(normalizedTitle);
    }
    if (!normalizedDescription.isBlank()) {
      builder.append(normalizedTitle.isBlank() ? " " : "：").append(normalizedDescription);
    }
    if (!normalizedUrl.isBlank()) {
      builder.append(' ').append(normalizedUrl);
    }
    return builder.toString().trim();
  }

  public static String buildFailureContent(String url, String reason) {
    StringBuilder builder = new StringBuilder();
    builder.append("# URL 内容抓取失败\n\n");
    builder.append("- 来源：").append(defaultString(url, "").trim()).append("\n");
    builder.append("- 原因：").append(defaultString(reason, "未知错误").trim());
    return truncate(builder.toString(), MAX_NORMALIZED_LENGTH);
  }

  private static UrlNormalizedResult failure(String url, String reason) {
    return new UrlNormalizedResult(false, buildFailureContent(url, reason), safeTitleFromUrl(url),
        "", defaultString(reason, "未知错误").trim());
  }

  private static boolean looksLikeHtml(String contentType, String responseBody) {
    String normalizedContentType = defaultString(contentType, "").toLowerCase(Locale.ROOT);
    if (normalizedContentType.contains("text/html")
        || normalizedContentType.contains("application/xhtml+xml")) {
      return true;
    }
    String preview = responseBody.substring(0, Math.min(responseBody.length(), 512))
        .toLowerCase(Locale.ROOT);
    return preview.contains("<html") || preview.contains("<body") || preview.contains("<article")
        || preview.contains("<!doctype html");
  }

  private static boolean looksLikePlainText(String contentType) {
    String normalizedContentType = defaultString(contentType, "").toLowerCase(Locale.ROOT);
    return normalizedContentType.isBlank() || normalizedContentType.contains("text/plain")
        || normalizedContentType.contains("application/json")
        || normalizedContentType.contains("application/xml")
        || normalizedContentType.contains("text/markdown");
  }

  private static String buildMarkdownLikeContent(String url, String title, String description,
      String bodyText) {
    String normalizedTitle = truncate(normalizeInlineText(firstNonBlank(title, safeTitleFromUrl(url))),
        MAX_TITLE_LENGTH);
    String normalizedDescription = truncate(normalizeInlineText(description), MAX_DESCRIPTION_LENGTH);
    String normalizedBody = truncate(normalizeText(bodyText), MAX_BODY_LENGTH);

    StringBuilder builder = new StringBuilder();
    builder.append("# ").append(normalizedTitle).append("\n\n");
    builder.append("- 来源：").append(defaultString(url, "").trim()).append("\n");
    if (!normalizedDescription.isBlank()) {
      builder.append("- 摘要：").append(normalizedDescription).append("\n");
    }
    builder.append("\n## 正文\n");
    builder.append(normalizedBody);
    return truncate(builder.toString(), MAX_NORMALIZED_LENGTH);
  }

  private static String normalizeTextFromHtml(String html) {
    String withoutComments = COMMENT_PATTERN.matcher(defaultString(html, "")).replaceAll(" ");
    String withoutScripts = SCRIPT_PATTERN.matcher(withoutComments).replaceAll(" ");
    String withBreaks = withoutScripts
        .replaceAll(
            "(?is)</?(p|div|article|section|main|aside|header|footer|li|ul|ol|h[1-6]|tr|td|th|blockquote|pre|br|hr)[^>]*>",
            "\n")
        .replaceAll("(?is)</?(span|strong|em|b|i)[^>]*>", " ");
    String noTags = TAG_PATTERN.matcher(withBreaks).replaceAll(" ");
    return normalizeText(HtmlUtils.htmlUnescape(noTags));
  }

  private static String normalizeText(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String normalized = HtmlUtils.htmlUnescape(text)
        .replace("\u00A0", " ")
        .replaceAll("\\r\\n?", "\n")
        .replaceAll("[\\t\\f\\x0B]+", " ");
    return Arrays.stream(normalized.split("\\n"))
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .map(line -> line.replaceAll("\\s{2,}", " "))
        .collect(Collectors.joining("\n\n"))
        .trim();
  }

  private static String extractTagText(Pattern pattern, String html) {
    Matcher matcher = pattern.matcher(defaultString(html, ""));
    if (!matcher.find()) {
      return "";
    }
    return HtmlUtils.htmlUnescape(matcher.group(1)).trim();
  }

  private static String extractMetaContent(String html, String... candidateNames) {
    Matcher matcher = META_TAG_PATTERN.matcher(defaultString(html, ""));
    while (matcher.find()) {
      Map<String, String> attributes = extractAttributes(matcher.group(1));
      String marker = firstNonBlank(attributes.get("property"), attributes.get("name"));
      if (marker.isBlank()) {
        continue;
      }
      for (String candidateName : candidateNames) {
        if (marker.equalsIgnoreCase(candidateName)) {
          return defaultString(attributes.get("content"), "").trim();
        }
      }
    }
    return "";
  }

  private static Map<String, String> extractAttributes(String rawAttributes) {
    Matcher matcher = ATTRIBUTE_PATTERN.matcher(defaultString(rawAttributes, ""));
    LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
    while (matcher.find()) {
      String value = firstNonBlank(matcher.group(3), matcher.group(4), matcher.group(5));
      attributes.put(matcher.group(1).toLowerCase(Locale.ROOT), defaultString(value, ""));
    }
    return attributes;
  }

  private static String safeTitleFromUrl(String url) {
    if (url == null || url.isBlank()) {
      return "未命名链接";
    }
    try {
      URI uri = URI.create(url.trim());
      String host = defaultString(uri.getHost(), "").trim();
      String path = defaultString(uri.getPath(), "").trim();
      String lastPath = "";
      if (!path.isBlank()) {
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
          if (!segments[i].isBlank()) {
            lastPath = segments[i];
            break;
          }
        }
      }
      String candidate = firstNonBlank(lastPath, host, url.trim());
      return truncate(candidate, MAX_TITLE_LENGTH);
    } catch (Exception ignored) {
      return truncate(url.trim(), MAX_TITLE_LENGTH);
    }
  }

  private static String buildExceptionMessage(Exception exception) {
    String message = defaultString(exception.getMessage(), exception.getClass().getSimpleName()).trim();
    return truncate("抓取异常: " + message, MAX_DESCRIPTION_LENGTH);
  }

  private static String normalizeInlineText(String text) {
    return defaultString(text, "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static String defaultString(String text, String defaultValue) {
    return text == null ? defaultValue : text;
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate.trim();
      }
    }
    return "";
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }

  public record UrlNormalizedResult(boolean success, String normalizedContent, String title,
      String description, String failureReason) {
  }
}
