/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_AGENT_ID;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_ID;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_INPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_MEMORY_TYPE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_OPERATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_OUTPUT_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_RERANK;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_RUN_ID;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_THRESHOLD;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_TOP_K;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiExtendedAttributes.GEN_AI_MEMORY_USER_ID;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of GenAI memory operation attributes.
 *
 * <p>This class delegates to a type-specific {@link GenAiMemoryAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class GenAiMemoryAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the GenAI Memory attributes extractor.
   *
   * @param getter the memory attributes getter
   * @param captureMessageContent whether to capture input/output messages (sensitive data)
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiMemoryAttributesGetter<REQUEST, RESPONSE> getter, boolean captureMessageContent) {
    return new GenAiMemoryAttributesExtractor<>(getter, captureMessageContent);
  }

  private final GenAiMemoryAttributesGetter<REQUEST, RESPONSE> getter;
  private final boolean captureMessageContent;

  private GenAiMemoryAttributesExtractor(
      GenAiMemoryAttributesGetter<REQUEST, RESPONSE> getter, boolean captureMessageContent) {
    this.getter = getter;
    this.captureMessageContent = captureMessageContent;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(
        GenAiAttributesExtractor.GEN_AI_OPERATION_NAME,
        GenAiExtendedAttributes.GenAiOperationNameValues.MEMORY_OPERATION);
    attributes.put(GEN_AI_MEMORY_OPERATION, getter.getMemoryOperation(request));
    attributes.put(GEN_AI_MEMORY_USER_ID, getter.getUserId(request));
    attributes.put(GEN_AI_MEMORY_AGENT_ID, getter.getAgentId(request));
    attributes.put(GEN_AI_MEMORY_RUN_ID, getter.getRunId(request));
    attributes.put(GEN_AI_MEMORY_ID, getter.getMemoryId(request));
    attributes.put(GEN_AI_MEMORY_MEMORY_TYPE, getter.getMemoryType(request));
    attributes.put(GEN_AI_MEMORY_TOP_K, getter.getTopK(request));
    attributes.put(GEN_AI_MEMORY_THRESHOLD, getter.getThreshold(request));
    attributes.put(GEN_AI_MEMORY_RERANK, getter.getRerank(request));
    if (captureMessageContent) {
      attributes.put(GEN_AI_MEMORY_INPUT_MESSAGES, getter.getInputMessages(request));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    if (captureMessageContent) {
      attributes.put(GEN_AI_MEMORY_OUTPUT_MESSAGES, getter.getOutputMessages(request, response));
    }
  }
}
