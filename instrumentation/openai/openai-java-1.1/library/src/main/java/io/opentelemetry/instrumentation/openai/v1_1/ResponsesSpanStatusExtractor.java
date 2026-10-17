/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.models.responses.Response;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

/**
 * Marks the span as failed when the API returns a response whose status is {@code failed}, which
 * the SDK delivers as a normal result (or as a {@code response.failed} stream event) rather than
 * throwing.
 */
final class ResponsesSpanStatusExtractor
    implements SpanStatusExtractor<ResponseCreateRequest, Response> {

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      ResponseCreateRequest request,
      @Nullable Response response,
      @Nullable Throwable error) {
    if (error == null && response != null && ResponsesAttributesGetter.isFailed(response)) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
      return;
    }
    SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
  }
}
