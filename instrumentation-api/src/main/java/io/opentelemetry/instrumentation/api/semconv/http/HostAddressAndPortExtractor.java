/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.instrumentation.api.semconv.http.HeaderParsingHelper.setPort;
import static io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesExtractor.firstHeaderValue;

import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;

/**
 * Extractor that gets server address and port from the HTTP Host header. This class is used as a
 * fallback when the {@link HttpClientAttributesGetter#getServerAddress(Object)} and {@link
 * HttpClientAttributesGetter#getServerPort(Object)} methods return null.
 *
 * @since 2.0.0
 */
public final class HostAddressAndPortExtractor<REQUEST>
    implements AddressAndPortExtractor<REQUEST> {

  private final HttpCommonAttributesGetter<REQUEST, ?> getter;

  public HostAddressAndPortExtractor(HttpCommonAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public void extract(AddressPortSink sink, REQUEST request) {
    String host = firstHeaderValue(getter.getHttpRequestHeader(request, "host"));
    if (host == null) {
      return;
    }

    int hostHeaderSeparator = host.indexOf(':');
    if (hostHeaderSeparator == -1) {
      sink.setAddress(host);
    } else {
      sink.setAddress(host.substring(0, hostHeaderSeparator));
      setPort(sink, host, hostHeaderSeparator + 1, host.length());
    }
  }
}
