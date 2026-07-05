/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import java.util.List;
import javax.annotation.Nullable;

/**
 * An interface for getting GenAI inference attributes (chat completion, text completion,
 * embeddings, generate content).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by {@link GenAiAttributesExtractor} to obtain the various
 * GenAI attributes in a type-generic way.
 *
 * <p>If an attribute is not available in this library, it is appropriate to return {@code null}
 * from the attribute methods, but implement as many as possible for best compliance with the
 * OpenTelemetry specification.
 */
public interface GenAiAttributesGetter<REQUEST, RESPONSE>
    extends GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  /** Returns the value of {@code gen_ai.provider.name}. */
  String getProviderName(REQUEST request);

  /**
   * Returns the legacy {@code gen_ai.system} value.
   *
   * @deprecated Use {@link #getProviderName} instead. This method will be removed in a future
   *     release once the migration period ends.
   */
  @Deprecated
  default String getSystem(REQUEST request) {
    return getProviderName(request);
  }

  /**
   * Returns the request model. For inference operations this also doubles as the {@link
   * #getOperationTarget operation target} used to build the span name.
   */
  @Nullable
  String getRequestModel(REQUEST request);

  /**
   * Returns the {@link GenAiOperationAttributesGetter#getOperationTarget operation target} for
   * inference operations, which is the request model by default.
   */
  @Override
  @Nullable
  default String getOperationTarget(REQUEST request) {
    return getRequestModel(request);
  }

  @Nullable
  Long getRequestSeed(REQUEST request);

  @Nullable
  List<String> getRequestEncodingFormats(REQUEST request);

  @Nullable
  Double getRequestFrequencyPenalty(REQUEST request);

  @Nullable
  Long getRequestMaxTokens(REQUEST request);

  @Nullable
  Double getRequestPresencePenalty(REQUEST request);

  @Nullable
  List<String> getRequestStopSequences(REQUEST request);

  @Nullable
  Double getRequestTemperature(REQUEST request);

  @Nullable
  Double getRequestTopK(REQUEST request);

  @Nullable
  Double getRequestTopP(REQUEST request);

  /**
   * Returns the value of {@code gen_ai.request.choice.count}. Conditionally Required: if available
   * in the request and the value is not 1.
   */
  @Nullable
  default Long getChoiceCount(REQUEST request) {
    return null;
  }

  /**
   * Returns the value of {@code gen_ai.output.type} (e.g. {@code text}, {@code json}, {@code
   * image}, {@code speech}).
   */
  @Nullable
  default String getOutputType(REQUEST request) {
    return null;
  }

  /** Returns the value of {@code gen_ai.conversation.id}. */
  @Nullable
  default String getConversationId(REQUEST request) {
    return null;
  }

  List<String> getResponseFinishReasons(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String getResponseId(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String getResponseModel(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  Long getUsageInputTokens(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  Long getUsageOutputTokens(REQUEST request, @Nullable RESPONSE response);
}
