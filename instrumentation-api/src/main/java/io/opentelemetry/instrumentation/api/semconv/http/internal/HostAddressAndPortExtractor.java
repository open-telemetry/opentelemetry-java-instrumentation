/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http.internal;

import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Extracts server address and port from the HTTP Host header.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
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

  @Nullable
  private static String firstHeaderValue(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }

  private static void setPort(AddressPortSink sink, String header, int start, int end) {
    if (start == end) {
      return;
    }
    try {
      sink.setPort(Integer.parseInt(header.substring(start, end)));
    } catch (NumberFormatException ignored) {
      // malformed port, ignoring
    }
  }
}
