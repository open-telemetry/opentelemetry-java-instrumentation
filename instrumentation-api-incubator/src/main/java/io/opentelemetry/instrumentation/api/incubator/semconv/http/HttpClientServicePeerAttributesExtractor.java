/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal.ServicePeerResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HostAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPort;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.ServerAddressAndPortExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code service.peer.name} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class HttpClientServicePeerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final AddressAndPortExtractor<REQUEST> addressAndPortExtractor;
  private final HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private final ServicePeerResolver servicePeerResolver;

  // visible for tests
  HttpClientServicePeerAttributesExtractor(
      AddressAndPortExtractor<REQUEST> addressAndPortExtractor,
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      ServicePeerResolver servicePeerResolver) {
    this.addressAndPortExtractor = addressAndPortExtractor;
    this.attributesGetter = attributesGetter;
    this.servicePeerResolver = servicePeerResolver;
  }

  /**
   * Returns a new {@link HttpClientServicePeerAttributesExtractor} that will use the passed {@code
   * attributesGetter} to extract server address and port (with fallback to the HTTP Host header).
   *
   * @param attributesGetter the getter to extract HTTP client attributes from the request
   * @param openTelemetry the OpenTelemetry instance to read service peer mapping configuration from
   */
  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  @SuppressWarnings("deprecation") // delegating to deprecated method for now
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter, OpenTelemetry openTelemetry) {
    return create(attributesGetter, new ServicePeerResolver(openTelemetry));
  }

  /**
   * This method is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   *
   * <p>This method only exists to bridge the deprecated {@code
   * HttpClientPeerServiceAttributesExtractor}.
   *
   * @deprecated Use {@link #create(HttpClientAttributesGetter, OpenTelemetry)} instead.
   */
  @Deprecated
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      ServicePeerResolver servicePeerResolver) {
    if (servicePeerResolver.isEmpty()) {
      return new EmptyAttributesExtractor<>();
    }
    AddressAndPortExtractor<REQUEST> addressAndPortExtractor =
        new ServerAddressAndPortExtractor<>(
            attributesGetter, new HostAddressAndPortExtractor<>(attributesGetter));
    return new HttpClientServicePeerAttributesExtractor<>(
        addressAndPortExtractor, attributesGetter, servicePeerResolver);
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

    servicePeerResolver.resolve(
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
