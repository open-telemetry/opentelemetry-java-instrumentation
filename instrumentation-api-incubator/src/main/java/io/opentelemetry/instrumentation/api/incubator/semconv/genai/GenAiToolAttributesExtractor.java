/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#execute-tool-span">GenAI
 * tool attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link GenAiToolAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public class GenAiToolAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from GenAiIncubatingAttributes
  private static final AttributeKey<String> GEN_AI_TOOL_CALL_ID = stringKey("gen_ai.tool.call.id");
  private static final AttributeKey<String> GEN_AI_TOOL_DESCRIPTION =
      stringKey("gen_ai.tool.description");
  private static final AttributeKey<String> GEN_AI_TOOL_NAME = stringKey("gen_ai.tool.name");
  private static final AttributeKey<String> GEN_AI_TOOL_TYPE = stringKey("gen_ai.tool.type");

  /** Creates the GenAI tool attributes extractor. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiToolAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    return new GenAiToolAttributesExtractor<>(attributesGetter);
  }

  private final GenAiToolAttributesGetter<REQUEST, RESPONSE> getter;

  private GenAiToolAttributesExtractor(GenAiToolAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, GEN_AI_OPERATION_NAME, getter.getOperationName(request));
    internalSet(attributes, GEN_AI_TOOL_DESCRIPTION, getter.getToolDescription(request));
    internalSet(attributes, GEN_AI_TOOL_NAME, getter.getToolName(request));
    internalSet(attributes, GEN_AI_TOOL_TYPE, getter.getToolType(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalSet(attributes, GEN_AI_TOOL_CALL_ID, getter.getToolCallId(request, response));
  }
}
