package top.rslly.iot.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RagConfigTest {

  @Test
  void knowledgeChatEmbeddingStoreShouldUseInMemoryStoreWhenEnabled() {
    RagConfig ragConfig = new RagConfig();
    ReflectionTestUtils.setField(ragConfig, "knowledgeChatEmbeddingStoreUrl", "http://127.0.0.1:18000");
    ReflectionTestUtils.setField(ragConfig, "knowledgeChatEmbeddingStoreInMemory", true);

    EmbeddingStore<TextSegment> embeddingStore = ragConfig.knowledgeChatEmbeddingStore();

    assertInstanceOf(InMemoryEmbeddingStore.class, embeddingStore);
  }

  @Test
  void knowledgeChatEmbeddingStoreShouldUseChromaStoreWhenDisabled() {
    RagConfig ragConfig = new RagConfig();
    ReflectionTestUtils.setField(ragConfig, "knowledgeChatEmbeddingStoreUrl", "http://127.0.0.1:18000");
    ReflectionTestUtils.setField(ragConfig, "knowledgeChatEmbeddingStoreInMemory", false);

    EmbeddingStore<TextSegment> embeddingStore = ragConfig.knowledgeChatEmbeddingStore();

    assertInstanceOf(ChromaEmbeddingStore.class, embeddingStore);
  }
}
