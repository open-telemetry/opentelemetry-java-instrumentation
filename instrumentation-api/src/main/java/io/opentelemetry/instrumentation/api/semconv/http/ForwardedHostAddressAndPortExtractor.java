/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.instrumentation.api.semconv.http.HeaderParsingHelper.notFound;
import static io.opentelemetry.instrumentation.api.semconv.http.HeaderParsingHelper.setPort;

import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import java.util.Locale;

final class ForwardedHostAddressAndPortExtractor<REQUEST>
    implements AddressAndPortExtractor<REQUEST> {

  private final HttpCommonAttributesGetter<REQUEST, ?> getter;

  ForwardedHostAddressAndPortExtractor(HttpCommonAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public void extract(AddressPortSink sink, REQUEST request) {
    // try Forwarded
    for (String forwarded : getter.getHttpRequestHeader(request, "forwarded")) {
      if (extractFromForwardedHeader(sink, forwarded)) {
        return;
      }
    }

    // try X-Forwarded-Host
    for (String forwardedHost : getter.getHttpRequestHeader(request, "x-forwarded-host")) {
      if (extractHost(sink, forwardedHost, 0, forwardedHost.length())) {
        return;
      }
    }

    // try :authority (HTTP 2.0 pseudo-header)
    for (String host : getter.getHttpRequestHeader(request, ":authority")) {
      if (extractHost(sink, host, 0, host.length())) {
        return;
      }
    }

    // try Host
    for (String host : getter.getHttpRequestHeader(request, "host")) {
      if (extractHost(sink, host, 0, host.length())) {
        return;
      }
    }
  }

  private static boolean extractFromForwardedHeader(AddressPortSink sink, String forwarded) {
    int start = forwarded.toLowerCase(Locale.ROOT).indexOf("host=");
    if (start < 0) {
      return false;
    }
    start += "host=".length(); // start is now the index after host=
    if (start >= forwarded.length() - 1) { // the value after host= must not be empty
      return false;
    }
    // find the end of the `host=<address>` section
    int end = forwarded.indexOf(';', start);
    if (end < 0) {
      end = forwarded.length();
    }
    return extractHost(sink, forwarded, start, end);
  }

  private static boolean extractHost(AddressPortSink sink, String host, int start, int end) {
    if (start >= end) {
      return false;
    }

    // skip quotes
    if (host.charAt(start) == '"') {
      // try to find the end of the quote
      int quoteEnd = host.indexOf('"', start + 1);
      if (notFound(quoteEnd, end)) {
        // malformed header value
        return false;
      }
      return extractHost(sink, host, start + 1, quoteEnd);
    }

    // an IPv6 address is enclosed in square brackets and contains ':' characters, so the address
    // ends at the closing ']' and the port, if any, follows it
    if (host.charAt(start) == '[') {
      int ipv6End = host.indexOf(']', start + 1);
      if (notFound(ipv6End, end)) {
        // malformed header value
        return false;
      }
      sink.setAddress(host.substring(start + 1, ipv6End));
      if (ipv6End + 1 < end && host.charAt(ipv6End + 1) == ':') {
        setPort(sink, host, ipv6End + 2, end);
      }
      return true;
    }

    int hostHeaderSeparator = host.indexOf(':', start);
    if (notFound(hostHeaderSeparator, end)) {
      sink.setAddress(host.substring(start, end));
    } else {
      sink.setAddress(host.substring(start, hostHeaderSeparator));
      setPort(sink, host, hostHeaderSeparator + 1, end);
    }

    return true;
  }
}
