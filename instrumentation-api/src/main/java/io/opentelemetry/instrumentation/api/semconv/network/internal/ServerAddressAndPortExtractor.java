/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HostAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerAddressAndPortExtractor<REQUEST>
    implements AddressAndPortExtractor<REQUEST> {

  private final ServerAttributesGetter<REQUEST> getter;
  private final AddressAndPortExtractor<REQUEST> fallbackAddressAndPortExtractor;

  public ServerAddressAndPortExtractor(
      ServerAttributesGetter<REQUEST> getter,
      AddressAndPortExtractor<REQUEST> fallbackAddressAndPortExtractor) {
    this.getter = getter;
    this.fallbackAddressAndPortExtractor = fallbackAddressAndPortExtractor;
  }

  /**
   * Creates a {@link ServerAddressAndPortExtractor} with a fallback to extract from the HTTP Host
   * header. This is the standard configuration for HTTP client instrumentation.
   *
   * @param httpAttributesGetter the HTTP attributes getter (which also implements ServerAttributesGetter)
   * @return a new extractor with Host header fallback
   */
  public static <REQUEST> ServerAddressAndPortExtractor<REQUEST> createWithHostHeaderFallback(
      HttpCommonAttributesGetter<REQUEST, ?> httpAttributesGetter) {
    // HttpCommonAttributesGetter extends ServerAttributesGetter, so this is safe
    @SuppressWarnings("unchecked")
    ServerAttributesGetter<REQUEST> serverGetter = (ServerAttributesGetter<REQUEST>) httpAttributesGetter;
    return new ServerAddressAndPortExtractor<>(
        serverGetter, new HostAddressAndPortExtractor<>(httpAttributesGetter));
  }

  @Override
  public void extract(AddressPortSink sink, REQUEST request) {
    String address = getter.getServerAddress(request);
    Integer port = getter.getServerPort(request);
    if (address == null && port == null) {
      fallbackAddressAndPortExtractor.extract(sink, request);
    } else {
      sink.setAddress(address);
      sink.setPort(port);
    }
  }
}
