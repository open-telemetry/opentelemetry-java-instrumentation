/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServiceMappingResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HostAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPort;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.ServerAddressAndPortExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of the configured {@code server.service.name} and {@code server.service.namespace} span
 * attributes.
 */
public final class HttpClientServerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final AddressAndPortExtractor<REQUEST> addressAndPortExtractor;
  private final HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private final ServiceMappingResolver serviceMappingResolver;

  /**
   * Returns a new {@link HttpClientServerServiceAttributesExtractor} that will use the passed
   * {@code attributesGetter} to extract server address and port (with fallback to the HTTP Host
   * header).
   *
   * @param attributesGetter the getter to extract HTTP client attributes from the request
   * @param openTelemetry the OpenTelemetry instance to read server service mapping configuration
   *     from
   */
  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter, OpenTelemetry openTelemetry) {
    ServiceMappingResolver serviceMappingResolver = new ServiceMappingResolver(openTelemetry);
    if (serviceMappingResolver.isEmpty()) {
      return new EmptyAttributesExtractor<>();
    }
    AddressAndPortExtractor<REQUEST> addressAndPortExtractor =
        new ServerAddressAndPortExtractor<>(
            attributesGetter, new HostAddressAndPortExtractor<>(attributesGetter));
    return new HttpClientServerServiceAttributesExtractor<>(
        addressAndPortExtractor, attributesGetter, serviceMappingResolver);
  }

  // visible for tests
  HttpClientServerServiceAttributesExtractor(
      AddressAndPortExtractor<REQUEST> addressAndPortExtractor,
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      ServiceMappingResolver serviceMappingResolver) {
    this.addressAndPortExtractor = addressAndPortExtractor;
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

    AddressAndPort addressAndPort = addressAndPortExtractor.extract(request);
    String host = addressAndPort.getAddress();
    if (host == null) {
      return;
    }

    serviceMappingResolver.resolve(
        host,
        addressAndPort.getPort(),
        () -> getUrlPath(attributesGetter, request),
        attributes::put);
  }

  @Nullable
  private String getUrlPath(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter, REQUEST request) {
    String urlFull = attributesGetter.getUrlFull(request);
    if (urlFull == null) {
      return null;
    }
    return UrlParser.getPath(urlFull);
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
