/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HeaderParsingHelper.setPort;
import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpCommonAttributesExtractor.firstHeaderValue;

import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPortExtractor;

final class HostAddressAndPortExtractor<REQUEST> implements AddressAndPortExtractor<REQUEST> {

  private final HttpCommonAttributesGetter<REQUEST, ?> getter;

  HostAddressAndPortExtractor(HttpCommonAttributesGetter<REQUEST, ?> getter) {
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
