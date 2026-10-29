/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.peer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.ServerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * Extractor of configured server service identity attributes.
 *
 * @deprecated Use {@link ServerServiceAttributesExtractor} instead. May be removed in the next
 *     minor release.
 */
@Deprecated // may be removed in the next minor release
public final class ServicePeerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final AttributesExtractor<REQUEST, RESPONSE> delegate;

  private ServicePeerAttributesExtractor(AttributesExtractor<REQUEST, RESPONSE> delegate) {
    this.delegate = delegate;
  }

  /**
   * Creates the server service attributes extractor.
   *
   * @deprecated Use {@link ServerServiceAttributesExtractor#create(ServerAttributesGetter,
   *     OpenTelemetry)} instead. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST> attributesGetter, OpenTelemetry openTelemetry) {
    return new ServicePeerAttributesExtractor<>(
        ServerServiceAttributesExtractor.create(attributesGetter, openTelemetry));
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
