/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class GenAiRetrievalAttributesExtractorTest {

  // GenAiIncubatingAttributes is deprecated in semconv 1.42 (GenAI conventions moved to the
  // semantic-conventions-genai repo), so the expected keys are declared inline here.
  private static final AttributeKey<String> GEN_AI_OPERATION_NAME =
      stringKey("gen_ai.operation.name");
  private static final AttributeKey<String> GEN_AI_DATA_SOURCE_ID =
      stringKey("gen_ai.data_source.id");
  private static final AttributeKey<String> GEN_AI_RETRIEVAL_QUERY_TEXT =
      stringKey("gen_ai.retrieval.query.text");
  private static final AttributeKey<Long> GEN_AI_RETRIEVAL_TOP_K =
      longKey("gen_ai.retrieval.top_k");

  @Test
  void shouldExtractRetrievalAttributesWithoutQueryTextByDefault() {
    RetrievalRequest request = new RetrievalRequest("products-index", "weather in Paris", 5L);

    AttributesExtractor<RetrievalRequest, Void> extractor =
        GenAiRetrievalAttributesExtractor.create(new TestRetrievalGetter());

    AttributesBuilder startBuilder = Attributes.builder();
    extractor.onStart(startBuilder, Context.root(), request);
    AttributesBuilder endBuilder = Attributes.builder();
    extractor.onEnd(endBuilder, Context.root(), request, null, null);

    Attributes expected =
        Attributes.builder()
            .put(GEN_AI_OPERATION_NAME, "retrieval")
            .put(GEN_AI_DATA_SOURCE_ID, "products-index")
            .put(GEN_AI_RETRIEVAL_TOP_K, 5L)
            .build();
    assertThat(startBuilder.build()).isEqualTo(expected);
    assertThat(endBuilder.build()).isEqualTo(Attributes.empty());
  }

  @Test
  void shouldExtractQueryTextWhenCaptureMessageContentEnabled() {
    RetrievalRequest request = new RetrievalRequest("products-index", "weather in Paris", 5L);

    AttributesExtractor<RetrievalRequest, Void> extractor =
        GenAiRetrievalAttributesExtractor.create(new TestRetrievalGetter(), true);

    AttributesBuilder startBuilder = Attributes.builder();
    extractor.onStart(startBuilder, Context.root(), request);

    Attributes expected =
        Attributes.builder()
            .put(GEN_AI_OPERATION_NAME, "retrieval")
            .put(GEN_AI_DATA_SOURCE_ID, "products-index")
            .put(GEN_AI_RETRIEVAL_QUERY_TEXT, "weather in Paris")
            .put(GEN_AI_RETRIEVAL_TOP_K, 5L)
            .build();
    assertThat(startBuilder.build()).isEqualTo(expected);
  }

  @Test
  void shouldOmitNullRetrievalAttributes() {
    RetrievalRequest request = new RetrievalRequest(null, null, null);

    AttributesExtractor<RetrievalRequest, Void> extractor =
        GenAiRetrievalAttributesExtractor.create(new TestRetrievalGetter(), true);

    AttributesBuilder startBuilder = Attributes.builder();
    extractor.onStart(startBuilder, Context.root(), request);

    assertThat(startBuilder.build())
        .isEqualTo(Attributes.builder().put(GEN_AI_OPERATION_NAME, "retrieval").build());
  }

  static class RetrievalRequest {
    @Nullable final String dataSourceId;
    @Nullable final String queryText;
    @Nullable final Long topK;

    RetrievalRequest(
        @Nullable String dataSourceId, @Nullable String queryText, @Nullable Long topK) {
      this.dataSourceId = dataSourceId;
      this.queryText = queryText;
      this.topK = topK;
    }
  }

  static class TestRetrievalGetter
      implements GenAiRetrievalAttributesGetter<RetrievalRequest, Void> {
    @Override
    public String getOperationName(RetrievalRequest request) {
      return "retrieval";
    }

    @Nullable
    @Override
    public String getDataSourceId(RetrievalRequest request) {
      return request.dataSourceId;
    }

    @Nullable
    @Override
    public String getQueryText(RetrievalRequest request) {
      return request.queryText;
    }

    @Nullable
    @Override
    public Long getTopK(RetrievalRequest request) {
      return request.topK;
    }
  }
}
