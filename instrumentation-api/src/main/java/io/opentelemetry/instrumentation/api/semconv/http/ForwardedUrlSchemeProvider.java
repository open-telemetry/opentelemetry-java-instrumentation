/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import java.util.Locale;
import java.util.function.Function;
import javax.annotation.Nullable;

final class ForwardedUrlSchemeProvider<REQUEST> implements Function<REQUEST, String> {

  private final HttpServerAttributesGetter<REQUEST, ?> getter;

  ForwardedUrlSchemeProvider(HttpServerAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String apply(REQUEST request) {
    // try Forwarded
    for (String forwarded : getter.getHttpRequestHeader(request, "forwarded")) {
      String proto = extractProtoFromForwardedHeader(forwarded);
      if (proto != null) {
        return proto;
      }
    }

    // try X-Forwarded-Proto
    for (String forwardedProto : getter.getHttpRequestHeader(request, "x-forwarded-proto")) {
      String proto = extractProtoFromForwardedProtoHeader(forwardedProto);
      if (proto != null) {
        return proto;
      }
    }

    return null;
  }

  /** Extract proto (aka scheme) from "Forwarded" http header. */
  @Nullable
  private static String extractProtoFromForwardedHeader(String forwarded) {
    int start = forwarded.toLowerCase(Locale.ROOT).indexOf("proto=");
    if (start < 0) {
      return null;
    }
    start += 6; // start is now the index after proto=
    if (start >= forwarded.length() - 1) { // the value after for= must not be empty
      return null;
    }
    return extractProto(forwarded, start);
  }

  /** Extract proto (aka scheme) from "X-Forwarded-Proto" http header. */
  @Nullable
  private static String extractProtoFromForwardedProtoHeader(String forwardedProto) {
    return extractProto(forwardedProto, 0);
  }

  @Nullable
  private static String extractProto(String forwarded, int start) {
    if (forwarded.length() == start) {
      return null;
    }
    if (forwarded.charAt(start) == '"') {
      return extractProto(forwarded, start + 1);
    }
    for (int i = start; i < forwarded.length(); i++) {
      char c = forwarded.charAt(i);
      if (c == ',' || c == ';' || c == '"') {
        if (i == start) { // empty string
          return null;
        }
        return forwarded.substring(start, i);
      }
    }
    return forwarded.substring(start);
  }
}
