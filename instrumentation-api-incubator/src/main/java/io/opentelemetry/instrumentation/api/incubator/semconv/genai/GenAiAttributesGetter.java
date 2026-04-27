/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import io.opentelemetry.api.common.Value;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An interface for getting GenAI inference attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link GenAiAttributesExtractor} to obtain the various
 * GenAI attributes in a type-generic way.
 *
 * <p>If an attribute is not available in this library, it is appropriate to return {@code null}
 * from the attribute methods, but implement as many as possible for best compliance with the
 * OpenTelemetry specification.
 */
public interface GenAiAttributesGetter<REQUEST, RESPONSE>
    extends GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  String getProviderName(REQUEST request);

  /**
   * Returns the old semconv {@code gen_ai.system} value. Defaults to {@link #getProviderName} for
   * instrumentations where the old and new values are the same.
   *
   * @deprecated Use {@link #getProviderName} instead.
   */
  @Deprecated
  default String getSystem(REQUEST request) {
    return getProviderName(request);
  }

  @Nullable
  String getRequestModel(REQUEST request);

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
   * Returns the number of choices requested. Conditionally Required: if available, in the request,
   * and the value is not 1.
   */
  @Nullable
  default Long getChoiceCount(REQUEST request) {
    return null;
  }

  /**
   * Returns the output type requested, e.g. {@code "text"} or {@code "json"}. Conditionally
   * Required: when available and the value is not {@code "text"}.
   */
  @Nullable
  default String getOutputType(REQUEST request) {
    return null;
  }

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

  /**
   * Returns the input messages sent to the model as a structured {@link Value}. Opt-In: only
   * populated when message content capture is enabled.
   */
  @Nullable
  default Value<?> getInputMessages(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the output messages from the model as a structured {@link Value}. Opt-In: only
   * populated when message content capture is enabled.
   */
  @Nullable
  default Value<?> getOutputMessages(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the system instructions as a structured {@link Value}. Opt-In: only populated when
   * message content capture is enabled.
   */
  @Nullable
  default Value<?> getSystemInstructions(REQUEST request) {
    return null;
  }

  /**
   * Returns the tool definitions as a structured {@link Value}. Opt-In: only populated when message
   * content capture is enabled.
   */
  @Nullable
  default Value<?> getToolDefinitions(REQUEST request) {
    return null;
  }
}
