/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import javax.annotation.Nullable;

/**
 * Extractor of configured server service identity attributes for HTTP clients.
 *
 * @deprecated Use {@link HttpClientServerServiceAttributesExtractor} instead. May be removed in the
 *     next minor release.
 */
@Deprecated // may be removed in the next minor release
public final class HttpClientServicePeerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final AttributesExtractor<REQUEST, RESPONSE> delegate;

  private HttpClientServicePeerAttributesExtractor(
      AttributesExtractor<REQUEST, RESPONSE> delegate) {
    this.delegate = delegate;
  }

  /**
   * Creates the HTTP client server service attributes extractor.
   *
   * @deprecated Use {@link HttpClientServerServiceAttributesExtractor#create(
   *     HttpClientAttributesGetter, OpenTelemetry)} instead. May be removed in the next minor
   *     release.
   */
  @Deprecated // may be removed in the next minor release
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter, OpenTelemetry openTelemetry) {
    return new HttpClientServicePeerAttributesExtractor<>(
        HttpClientServerServiceAttributesExtractor.create(attributesGetter, openTelemetry));
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
