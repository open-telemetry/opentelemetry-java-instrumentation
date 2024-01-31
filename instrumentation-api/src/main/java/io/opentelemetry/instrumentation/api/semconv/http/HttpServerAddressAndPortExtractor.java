/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.instrumentation.api.semconv.http.HeaderParsingHelper.notFound;

import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import java.util.Locale;

final class HttpServerAddressAndPortExtractor<REQUEST> implements AddressAndPortExtractor<REQUEST> {

  private final HttpServerAttributesGetter<REQUEST, ?> getter;

  HttpServerAddressAndPortExtractor(HttpServerAttributesGetter<REQUEST, ?> getter) {
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

    // try X-Forwarded-For
    for (String forwardedFor : getter.getHttpRequestHeader(request, "x-forwarded-for")) {
      if (extractFromForwardedForHeader(sink, forwardedFor)) {
        return;
      }
    }

    // use network.peer.address and network.peer.port
    sink.setAddress(getter.getNetworkPeerAddress(request, null));
    Integer port = getter.getNetworkPeerPort(request, null);
    if (port != null && port > 0) {
      sink.setPort(port);
    }
  }

  private static boolean extractFromForwardedHeader(AddressPortSink sink, String forwarded) {
    int start = forwarded.toLowerCase(Locale.ROOT).indexOf("for=");
    if (start < 0) {
      return false;
    }
    start += "for=".length(); // start is now the index after for=
    if (start >= forwarded.length() - 1) { // the value after for= must not be empty
      return false;
    }
    // find the end of the `for=<address>` section
    int end = forwarded.indexOf(';', start);
    if (end < 0) {
      end = forwarded.length();
    }
    return extractClientInfo(sink, forwarded, start, end);
  }

  private static boolean extractFromForwardedForHeader(AddressPortSink sink, String forwardedFor) {
    return extractClientInfo(sink, forwardedFor, 0, forwardedFor.length());
  }

  // from https://www.rfc-editor.org/rfc/rfc7239
  //  "Note that IPv6 addresses may not be quoted in
  //   X-Forwarded-For and may not be enclosed by square brackets, but they
  //   are quoted and enclosed in square brackets in Forwarded"
  // and also (applying to Forwarded but not X-Forwarded-For)
  //  "It is important to note that an IPv6 address and any nodename with
  //   node-port specified MUST be quoted, since ':' is not an allowed
  //   character in 'token'."
  private static boolean extractClientInfo(
      AddressPortSink sink, String forwarded, int start, int end) {
    if (start >= end) {
      return false;
    }

    // skip quotes
    if (forwarded.charAt(start) == '"') {
      // try to find the end of the quote
      int quoteEnd = forwarded.indexOf('"', start + 1);
      if (notFound(quoteEnd, end)) {
        // malformed header value
        return false;
      }
      return extractClientInfo(sink, forwarded, start + 1, quoteEnd);
    }

    // ipv6 address enclosed in square brackets case
    if (forwarded.charAt(start) == '[') {
      int ipv6End = forwarded.indexOf(']', start + 1);
      if (notFound(ipv6End, end)) {
        // malformed header value
        return false;
      }
      sink.setAddress(forwarded.substring(start + 1, ipv6End));

      return true;
    }

    // try to match either ipv4 or ipv6 without brackets
    boolean inIpv4 = false;
    for (int i = start; i < end; ++i) {
      char c = forwarded.charAt(i);

      // dots only appear in ipv4
      if (c == '.') {
        inIpv4 = true;
      }

      // find the character terminating the address
      boolean isIpv4PortSeparator = inIpv4 && c == ':';
      if (c == ',' || c == ';' || c == '"' || isIpv4PortSeparator) {
        // empty string
        if (i == start) {
          return false;
        }

        sink.setAddress(forwarded.substring(start, i));
        return true;
      }
    }

    // just an address without a port
    sink.setAddress(forwarded.substring(start, end));
    return true;
  }
}
