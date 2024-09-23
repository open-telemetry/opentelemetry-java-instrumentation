/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

/**
 * An attribute extractor that reports implementing HTTP client semantic conventions. Adding this
 * extractor suppresses nested HTTP client instrumentations similarly to how using {@link
 * HttpClientAttributesExtractor} would.
 */
class AwsSdkHttpClientSuppressionAttributesExtractor
    implements AttributesExtractor<ExecutionAttributes, Response>, SpanKeyProvider {

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ExecutionAttributes executionAttributes) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {}

  @Nullable
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.HTTP_CLIENT;
  }
}
