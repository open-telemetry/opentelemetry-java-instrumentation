/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.tool;

import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_CALL_ARGUMENTS;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_CALL_ID;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_CALL_RESULT;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_DESCRIPTION;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.genai.incubating.GenAiToolIncubatingAttributes.GEN_AI_TOOL_TYPE;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.genai.MessageCaptureOptions;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

public final class GenAiToolAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /** Creates the GenAI attributes extractor. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiToolAttributesGetter<REQUEST, RESPONSE> attributesGetter, MessageCaptureOptions messageCaptureOptions) {
    return new GenAiToolAttributesExtractor<>(attributesGetter, messageCaptureOptions);
  }

  private final GenAiToolAttributesGetter<REQUEST, RESPONSE> getter;

  private final MessageCaptureOptions messageCaptureOptions;

  private GenAiToolAttributesExtractor(
      GenAiToolAttributesGetter<REQUEST, RESPONSE> getter,
      MessageCaptureOptions messageCaptureOptions) {
    this.getter = getter;
    this.messageCaptureOptions = messageCaptureOptions;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, GEN_AI_OPERATION_NAME, getter.getOperationName(request));
    internalSet(attributes, GEN_AI_TOOL_DESCRIPTION, getter.getToolDescription(request));
    internalSet(attributes, GEN_AI_TOOL_NAME, getter.getToolName(request));
    internalSet(attributes, GEN_AI_TOOL_TYPE, getter.getToolType(request));
    if (messageCaptureOptions.captureMessageContent()) {
      internalSet(attributes, GEN_AI_TOOL_CALL_ARGUMENTS, getter.getToolCallArguments(request));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalSet(attributes, GEN_AI_TOOL_CALL_ID, getter.getToolCallId(request, response));
    if (messageCaptureOptions.captureMessageContent()) {
      internalSet(attributes, GEN_AI_TOOL_CALL_RESULT, getter.getToolCallResult(request, response));
    }
  }
}
