/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * An attribute extractor that forwards to a delegate without exposing the delegate's {@code
 * SpanKeyProvider} span key, so the resulting span neither suppresses nor is suppressed by spans
 * carrying that key. The {@code SQSEvent} batch span omits {@code CONSUMER_PROCESS} so that the
 * per-message process spans nested under it are still recorded, and the per-message spans omit it
 * under the legacy semantic conventions so that their pre-v1.43 suppression behavior is preserved.
 */
final class SpanKeyOmittingAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final AttributesExtractor<REQUEST, RESPONSE> delegate;

  SpanKeyOmittingAttributesExtractor(AttributesExtractor<REQUEST, RESPONSE> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    delegate.onStart(attributes, parentContext, request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    delegate.onEnd(attributes, context, request, response, error);
  }
}
