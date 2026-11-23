/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServiceMappingResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * Extractor of the configured {@code server.service.name} and {@code server.service.namespace} span
 * attributes.
 */
public final class ServerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final ServerAttributesGetter<REQUEST> attributesGetter;
  private final ServiceMappingResolver serviceMappingResolver;

  /**
   * Returns a new {@link ServerServiceAttributesExtractor} that will use the passed {@code
   * attributesGetter} instance to determine the values of the configured server service identity
   * attributes.
   *
   * @param attributesGetter the getter to extract server address and port from the request
   * @param openTelemetry the OpenTelemetry instance to read server service mapping configuration
   *     from
   */
  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST> attributesGetter, OpenTelemetry openTelemetry) {
    ServiceMappingResolver serviceMappingResolver = new ServiceMappingResolver(openTelemetry);
    if (serviceMappingResolver.isEmpty()) {
      return new EmptyAttributesExtractor<>();
    }
    return new ServerServiceAttributesExtractor<>(attributesGetter, serviceMappingResolver);
  }

  // visible for tests
  ServerServiceAttributesExtractor(
      ServerAttributesGetter<REQUEST> attributesGetter,
      ServiceMappingResolver serviceMappingResolver) {
    this.attributesGetter = attributesGetter;
    this.serviceMappingResolver = serviceMappingResolver;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    String serverAddress = attributesGetter.getServerAddress(request);
    if (serverAddress == null) {
      return;
    }

    serviceMappingResolver.resolve(
        serverAddress, attributesGetter.getServerPort(request), () -> null, attributes::put);
  }

  private static final class EmptyAttributesExtractor<REQUEST, RESPONSE>
      implements AttributesExtractor<REQUEST, RESPONSE> {

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        REQUEST request,
        @Nullable RESPONSE response,
        @Nullable Throwable error) {}
  }
}
