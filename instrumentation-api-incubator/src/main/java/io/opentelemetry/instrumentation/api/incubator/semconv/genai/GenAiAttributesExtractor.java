/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.common.AttributeKey.valueKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitGenAiLatestExperimentalSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldGenAiSemconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Extractor of GenAI inference attributes.
 *
 * <p>This class delegates to a type-specific {@link GenAiAttributesGetter} for individual attribute
 * extraction from request/response objects.
 */
public final class GenAiAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from GenAiIncubatingAttributes
  private static final AttributeKey<String> GEN_AI_CONVERSATION_ID =
      stringKey("gen_ai.conversation.id");
  static final AttributeKey<String> GEN_AI_OPERATION_NAME = stringKey("gen_ai.operation.name");
  private static final AttributeKey<String> GEN_AI_OUTPUT_TYPE = stringKey("gen_ai.output.type");
  static final AttributeKey<String> GEN_AI_PROVIDER_NAME = stringKey("gen_ai.provider.name");
  static final AttributeKey<String> GEN_AI_SYSTEM = stringKey("gen_ai.system");
  private static final AttributeKey<Long> GEN_AI_REQUEST_CHOICE_COUNT =
      longKey("gen_ai.request.choice.count");
  private static final AttributeKey<List<String>> GEN_AI_REQUEST_ENCODING_FORMATS =
      stringArrayKey("gen_ai.request.encoding_formats");
  private static final AttributeKey<Double> GEN_AI_REQUEST_FREQUENCY_PENALTY =
      doubleKey("gen_ai.request.frequency_penalty");
  private static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS =
      longKey("gen_ai.request.max_tokens");
  static final AttributeKey<String> GEN_AI_REQUEST_MODEL = stringKey("gen_ai.request.model");
  private static final AttributeKey<Double> GEN_AI_REQUEST_PRESENCE_PENALTY =
      doubleKey("gen_ai.request.presence_penalty");
  private static final AttributeKey<Long> GEN_AI_REQUEST_SEED = longKey("gen_ai.request.seed");
  private static final AttributeKey<List<String>> GEN_AI_REQUEST_STOP_SEQUENCES =
      stringArrayKey("gen_ai.request.stop_sequences");
  private static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE =
      doubleKey("gen_ai.request.temperature");
  static final AttributeKey<Double> GEN_AI_REQUEST_TOP_K = doubleKey("gen_ai.request.top_k");
  private static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P =
      doubleKey("gen_ai.request.top_p");
  private static final AttributeKey<List<String>> GEN_AI_RESPONSE_FINISH_REASONS =
      stringArrayKey("gen_ai.response.finish_reasons");
  private static final AttributeKey<String> GEN_AI_RESPONSE_ID = stringKey("gen_ai.response.id");
  static final AttributeKey<String> GEN_AI_RESPONSE_MODEL = stringKey("gen_ai.response.model");
  static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = longKey("gen_ai.usage.input_tokens");
  static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS =
      longKey("gen_ai.usage.output_tokens");
  private static final AttributeKey<Value<?>> GEN_AI_INPUT_MESSAGES =
      valueKey("gen_ai.input.messages");
  private static final AttributeKey<Value<?>> GEN_AI_OUTPUT_MESSAGES =
      valueKey("gen_ai.output.messages");
  private static final AttributeKey<Value<?>> GEN_AI_SYSTEM_INSTRUCTIONS =
      valueKey("gen_ai.system_instructions");
  private static final AttributeKey<Value<?>> GEN_AI_TOOL_DEFINITIONS =
      valueKey("gen_ai.tool.definitions");

  /** Creates the GenAI attributes extractor with default settings. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    return builder(attributesGetter).build();
  }

  /** Returns a new {@link GenAiAttributesExtractorBuilder}. */
  public static <REQUEST, RESPONSE> GenAiAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      GenAiAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    return new GenAiAttributesExtractorBuilder<>(attributesGetter);
  }

  private final GenAiAttributesGetter<REQUEST, RESPONSE> getter;
  private final boolean captureMessageContent;

  GenAiAttributesExtractor(GenAiAttributesExtractorBuilder<REQUEST, RESPONSE> builder) {
    this.getter = builder.getter;
    this.captureMessageContent = builder.captureMessageContent;
  }

  @Override
  // calling deprecated getSystem() for backward-compatible dual-emit
  @SuppressWarnings("deprecation")
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(GEN_AI_CONVERSATION_ID, getter.getConversationId(request));
    attributes.put(GEN_AI_OPERATION_NAME, getter.getOperationName(request));
    attributes.put(GEN_AI_OUTPUT_TYPE, getter.getOutputType(request));
    if (emitGenAiLatestExperimentalSemconv()) {
      attributes.put(GEN_AI_PROVIDER_NAME, getter.getProviderName(request));
    }
    if (emitOldGenAiSemconv()) {
      attributes.put(GEN_AI_SYSTEM, getter.getSystem(request));
    }
    Long choiceCount = getter.getChoiceCount(request);
    if (choiceCount != null && choiceCount != 1) {
      attributes.put(GEN_AI_REQUEST_CHOICE_COUNT, choiceCount);
    }
    attributes.put(GEN_AI_REQUEST_MODEL, getter.getRequestModel(request));
    attributes.put(GEN_AI_REQUEST_SEED, getter.getRequestSeed(request));
    attributes.put(GEN_AI_REQUEST_ENCODING_FORMATS, getter.getRequestEncodingFormats(request));
    attributes.put(GEN_AI_REQUEST_FREQUENCY_PENALTY, getter.getRequestFrequencyPenalty(request));
    attributes.put(GEN_AI_REQUEST_MAX_TOKENS, getter.getRequestMaxTokens(request));
    attributes.put(GEN_AI_REQUEST_PRESENCE_PENALTY, getter.getRequestPresencePenalty(request));
    attributes.put(GEN_AI_REQUEST_STOP_SEQUENCES, getter.getRequestStopSequences(request));
    attributes.put(GEN_AI_REQUEST_TEMPERATURE, getter.getRequestTemperature(request));
    attributes.put(GEN_AI_REQUEST_TOP_K, getter.getRequestTopK(request));
    attributes.put(GEN_AI_REQUEST_TOP_P, getter.getRequestTopP(request));
    if (emitGenAiLatestExperimentalSemconv() && captureMessageContent) {
      attributes.put(GEN_AI_SYSTEM_INSTRUCTIONS, getter.getSystemInstructions(request));
      attributes.put(GEN_AI_TOOL_DEFINITIONS, getter.getToolDefinitions(request));
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    List<String> finishReasons = getter.getResponseFinishReasons(request, response);
    if (finishReasons != null && !finishReasons.isEmpty()) {
      attributes.put(GEN_AI_RESPONSE_FINISH_REASONS, finishReasons);
    }
    attributes.put(GEN_AI_RESPONSE_ID, getter.getResponseId(request, response));
    attributes.put(GEN_AI_RESPONSE_MODEL, getter.getResponseModel(request, response));
    attributes.put(GEN_AI_USAGE_INPUT_TOKENS, getter.getUsageInputTokens(request, response));
    attributes.put(GEN_AI_USAGE_OUTPUT_TOKENS, getter.getUsageOutputTokens(request, response));
    if (emitGenAiLatestExperimentalSemconv() && captureMessageContent) {
      attributes.put(GEN_AI_INPUT_MESSAGES, getter.getInputMessages(request, response));
      attributes.put(GEN_AI_OUTPUT_MESSAGES, getter.getOutputMessages(request, response));
    }
  }
}
