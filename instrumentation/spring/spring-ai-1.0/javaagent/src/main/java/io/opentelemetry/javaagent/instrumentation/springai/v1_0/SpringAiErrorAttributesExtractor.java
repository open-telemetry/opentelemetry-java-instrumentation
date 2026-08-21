/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.springframework.ai.chat.model.ChatResponse;

final class SpringAiErrorAttributesExtractor
    implements AttributesExtractor<SpringAiRequest, ChatResponse> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, SpringAiRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SpringAiRequest request,
      @Nullable ChatResponse response,
      @Nullable Throwable error) {
    if (error != null) {
      attributes.put(ERROR_TYPE, error.getClass().getName());
    }
  }
}
