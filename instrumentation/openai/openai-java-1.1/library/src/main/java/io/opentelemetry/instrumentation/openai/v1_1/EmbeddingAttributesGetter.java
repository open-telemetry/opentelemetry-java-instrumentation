/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.Collections.singletonList;

import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

enum EmbeddingAttributesGetter
    implements GenAiAttributesGetter<EmbeddingCreateParams, CreateEmbeddingResponse> {
  INSTANCE;

  @Override
  public String getOperationName(EmbeddingCreateParams request) {
    return GenAiAttributes.GenAiOperationNameIncubatingValues.EMBEDDINGS;
  }

  @Override
  public String getSystem(EmbeddingCreateParams request) {
    return GenAiAttributes.GenAiSystemIncubatingValues.OPENAI;
  }

  @Override
  public String getRequestModel(EmbeddingCreateParams request) {
    return request.model().asString();
  }

  @Nullable
  @Override
  public Long getRequestSeed(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getRequestEncodingFormats(EmbeddingCreateParams request) {
    return request.encodingFormat().map(f -> singletonList(f.asString())).orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestFrequencyPenalty(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public Long getRequestMaxTokens(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestPresencePenalty(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getRequestStopSequences(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTemperature(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTopK(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTopP(EmbeddingCreateParams request) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getResponseFinishReasons(
      EmbeddingCreateParams request, @Nullable CreateEmbeddingResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String getResponseId(
      EmbeddingCreateParams request, @Nullable CreateEmbeddingResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String getResponseModel(
      EmbeddingCreateParams request, @Nullable CreateEmbeddingResponse response) {
    if (response == null) {
      return null;
    }
    return response.model();
  }

  @Nullable
  @Override
  public Long getUsageInputTokens(
      EmbeddingCreateParams request, @Nullable CreateEmbeddingResponse response) {
    if (response == null) {
      return null;
    }
    return response.usage().promptTokens();
  }

  @Nullable
  @Override
  public Long getUsageOutputTokens(
      EmbeddingCreateParams request, @Nullable CreateEmbeddingResponse response) {
    return null;
  }
}
