package top.rslly.iot.utility.input;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InputContentAutoTaggerTest {
  private final InputContentAutoTagger inputContentAutoTagger = new InputContentAutoTagger();

  @Test
  void shouldClassifyTaskTextAndExtractTags() {
    var result = inputContentAutoTagger.analyze("text", null, "明天安排修复登录 bug 并同步上线 #release");

    Assertions.assertEquals("task", result.category());
    Assertions.assertTrue(result.tags().contains("text"));
    Assertions.assertTrue(result.tags().contains("task"));
    Assertions.assertTrue(result.tags().contains("schedule"));
    Assertions.assertTrue(result.tags().contains("bugfix"));
    Assertions.assertTrue(result.tags().contains("release"));
  }

  @Test
  void shouldClassifyReferenceUrlAndExtractHostTag() {
    var result = inputContentAutoTagger.analyze("url", "https://docs.github.com/en/actions",
        "# GitHub Actions\n\nThis is a setup guide for workflows.");

    Assertions.assertEquals("knowledge", result.category());
    Assertions.assertTrue(result.tags().contains("url"));
    Assertions.assertTrue(result.tags().contains("docs.github.com"));
    Assertions.assertTrue(result.tags().contains("knowledge"));
  }

  @Test
  void parseTagsShouldSplitCommaSeparatedValue() {
    var tags = inputContentAutoTagger.parseTags("text,knowledge,docs.github.com");

    Assertions.assertEquals(3, tags.size());
    Assertions.assertEquals("text", tags.get(0));
    Assertions.assertEquals("knowledge", tags.get(1));
    Assertions.assertEquals("docs.github.com", tags.get(2));
  }
}
