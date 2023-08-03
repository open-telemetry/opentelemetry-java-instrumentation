/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractClientIpFromForwardedForHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractClientIpFromForwardedHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpCommonAttributesExtractor.firstHeaderValue;

import io.opentelemetry.instrumentation.api.instrumenter.network.internal.FallbackAddressPortExtractor;

final class ClientAddressAndPortExtractor<REQUEST>
    implements FallbackAddressPortExtractor<REQUEST> {

  private final HttpServerAttributesGetter<REQUEST, ?> getter;

  ClientAddressAndPortExtractor(HttpServerAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public void extract(AddressPortSink sink, REQUEST request) {
    // try Forwarded
    String forwarded = firstHeaderValue(getter.getHttpRequestHeader(request, "forwarded"));
    if (forwarded != null) {
      forwarded = extractClientIpFromForwardedHeader(forwarded);
      if (forwarded != null) {
        sink.setAddress(forwarded);
        return;
      }
    }

    // try X-Forwarded-For
    forwarded = firstHeaderValue(getter.getHttpRequestHeader(request, "x-forwarded-for"));
    if (forwarded != null) {
      sink.setAddress(extractClientIpFromForwardedForHeader(forwarded));
    }

    // TODO: client.port will be implemented in a future PR
  }
}
