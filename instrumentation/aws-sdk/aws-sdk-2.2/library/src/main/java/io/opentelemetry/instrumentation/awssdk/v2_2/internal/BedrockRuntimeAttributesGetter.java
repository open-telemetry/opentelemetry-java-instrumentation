/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

enum BedrockRuntimeAttributesGetter
    implements GenAiAttributesGetter<ExecutionAttributes, Response> {
  INSTANCE;

  static final class GenAiSystemIncubatingValues {
    static final String AWS_BEDROCK = "aws.bedrock";

    private GenAiSystemIncubatingValues() {}
  }

  @Override
  public String getOperationName(ExecutionAttributes executionAttributes) {
    return BedrockRuntimeAccess.getOperationName(executionAttributes);
  }

  @Override
  public String getSystem(ExecutionAttributes executionAttributes) {
    return GenAiSystemIncubatingValues.AWS_BEDROCK;
  }

  @Nullable
  @Override
  public String getRequestModel(ExecutionAttributes executionAttributes) {
    return BedrockRuntimeAccess.getModelId(executionAttributes);
  }

  @Nullable
  @Override
  public Long getRequestSeed(ExecutionAttributes executionAttributes) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getRequestEncodingFormats(ExecutionAttributes executionAttributes) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestFrequencyPenalty(ExecutionAttributes executionAttributes) {
    return null;
  }

  @Nullable
  @Override
  public Long getRequestMaxTokens(ExecutionAttributes executionAttributes) {
    return BedrockRuntimeAccess.getMaxTokens(executionAttributes);
  }

  @Nullable
  @Override
  public Double getRequestPresencePenalty(ExecutionAttributes executionAttributes) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getRequestStopSequences(ExecutionAttributes executionAttributes) {
    return BedrockRuntimeAccess.getStopSequences(executionAttributes);
  }

  @Nullable
  @Override
  public Double getRequestTemperature(ExecutionAttributes executionAttributes) {
    return BedrockRuntimeAccess.getTemperature(executionAttributes);
  }

  @Nullable
  @Override
  public Double getRequestTopK(ExecutionAttributes executionAttributes) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTopP(ExecutionAttributes executionAttributes) {
    return BedrockRuntimeAccess.getTopP(executionAttributes);
  }

  @Override
  public List<String> getResponseFinishReasons(
      ExecutionAttributes executionAttributes, @Nullable Response response) {
    if (response == null) {
      return emptyList();
    }
    List<String> stopReasons = BedrockRuntimeAccess.getStopReasons(executionAttributes, response);
    if (stopReasons == null) {
      return Collections.emptyList();
    }
    return stopReasons;
  }

  @Nullable
  @Override
  public String getResponseId(ExecutionAttributes executionAttributes, Response response) {
    return null;
  }

  @Nullable
  @Override
  public String getResponseModel(ExecutionAttributes executionAttributes, Response response) {
    return null;
  }

  @Nullable
  @Override
  public Long getUsageInputTokens(
      ExecutionAttributes executionAttributes, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    return BedrockRuntimeAccess.getUsageInputTokens(executionAttributes, response);
  }

  @Nullable
  @Override
  public Long getUsageOutputTokens(
      ExecutionAttributes executionAttributes, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    return BedrockRuntimeAccess.getUsageOutputTokens(executionAttributes, response);
  }
}
