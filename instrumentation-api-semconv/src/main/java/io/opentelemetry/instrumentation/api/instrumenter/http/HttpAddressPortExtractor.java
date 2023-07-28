/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpCommonAttributesExtractor.firstHeaderValue;
import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.instrumenter.network.internal.FallbackAddressPortExtractor;
import java.util.logging.Logger;

final class HttpAddressPortExtractor<REQUEST> implements FallbackAddressPortExtractor<REQUEST> {

  private static final Logger logger = Logger.getLogger(HttpCommonAttributesGetter.class.getName());

  private final HttpCommonAttributesGetter<REQUEST, ?> getter;

  HttpAddressPortExtractor(HttpCommonAttributesGetter<REQUEST, ?> getter) {
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
      return;
    }

    sink.setAddress(host.substring(0, hostHeaderSeparator));
    try {
      sink.setPort(Integer.parseInt(host.substring(hostHeaderSeparator + 1)));
    } catch (NumberFormatException e) {
      logger.log(FINE, e.getMessage(), e);
    }
  }
}
