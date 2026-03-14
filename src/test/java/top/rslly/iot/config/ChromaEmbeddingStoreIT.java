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
package top.rslly.iot.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test that validates {@link RagConfig} wires a real Chroma
 * embedding store correctly.
 *
 * <p>This test is <strong>opt-in</strong> and is skipped by default.
 * Enable it by setting the environment variable:
 *
 * <pre>
 *   CHROMA_VALIDATION_ENABLED=true
 * </pre>
 *
 * Point it at a running Chroma instance via:
 *
 * <pre>
 *   CHROMA_VALIDATION_URL=http://127.0.0.1:18000   (default)
 * </pre>
 *
 * Quick start:
 * <pre>
 *   docker compose -f docker/docker-compose.chroma-validation.yml up -d
 *   CHROMA_VALIDATION_ENABLED=true ./mvnw -q -Dtest=ChromaEmbeddingStoreIT test
 * </pre>
 */
class ChromaEmbeddingStoreIT {

  private static final String ENV_ENABLED = "CHROMA_VALIDATION_ENABLED";
  private static final String ENV_URL = "CHROMA_VALIDATION_URL";
  private static final String DEFAULT_URL = "http://127.0.0.1:18000";

  private String chromaUrl;

  @BeforeEach
  void skipUnlessEnabled() {
    assumeTrue(
        "true".equalsIgnoreCase(System.getenv(ENV_ENABLED)),
        "Chroma validation is disabled. Set " + ENV_ENABLED + "=true to enable this test.");
    chromaUrl = System.getenv(ENV_URL) != null ? System.getenv(ENV_URL) : DEFAULT_URL;
  }

  @Test
  void ragConfigShouldWireChromaStoreWhenInMemoryDisabled() {
    RagConfig ragConfig = new RagConfig();
    ReflectionTestUtils.setField(ragConfig, "knowledgeChatEmbeddingStoreUrl", chromaUrl);
    ReflectionTestUtils.setField(ragConfig, "knowledgeChatEmbeddingStoreInMemory", false);

    EmbeddingStore<TextSegment> store = ragConfig.knowledgeChatEmbeddingStore();

    assertInstanceOf(ChromaEmbeddingStore.class, store,
        "Expected ChromaEmbeddingStore when in-memory flag is false");
  }

  @Test
  void chromaStoreShouldAcceptAndReturnEmbeddings() {
    ChromaEmbeddingStore store = ChromaEmbeddingStore.builder()
        .baseUrl(chromaUrl)
        .collectionName("it_chroma_validation_" + System.currentTimeMillis())
        .logRequests(false)
        .logResponses(false)
        .build();

    // Minimal 3-dim embedding just to verify connectivity — not a real model output.
    float[] vector = {0.1f, 0.2f, 0.3f};
    Embedding embedding = new Embedding(vector);
    TextSegment segment = TextSegment.from("hello chroma", Metadata.from("source", "test"));

    String id = store.add(embedding, segment);

    assertNotNull(id, "Chroma should return a non-null document id after add()");
    assertFalse(id.isBlank(), "Chroma should return a non-blank document id after add()");
  }
}
