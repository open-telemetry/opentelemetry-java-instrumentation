/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SchemaUrlProvider;
import javax.annotation.Nullable;

/**
 * An attribute extractor that forwards to a delegate without exposing the delegate's {@code
 * SpanKeyProvider} span key, so the resulting span neither suppresses nor is suppressed by spans
 * carrying that key. Lambda explicitly selects each {@code SQSEvent} batch and per-message
 * operation, so these extractors omit the generic process key to keep independently selected
 * operations from suppressing one another.
 */
final class SpanKeyOmittingAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE>, SchemaUrlProvider {

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

  @Nullable
  @Override
  public String internalGetSchemaUrl() {
    return delegate instanceof SchemaUrlProvider
        ? ((SchemaUrlProvider) delegate).internalGetSchemaUrl()
        : null;
  }
}
