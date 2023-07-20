/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractProtoFromForwardedHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractProtoFromForwardedProtoHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpCommonAttributesExtractor.firstHeaderValue;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.function.Function;

final class AlternateUrlSchemeProvider<REQUEST> implements Function<REQUEST, String> {

  // if set to true, the instrumentation will prefer the scheme from Forwarded/X-Forwarded-Proto
  // headers over the one extracted from the URL
  private static final boolean PREFER_FORWARDED_URL_SCHEME =
      ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.http.prefer-forwarded-url-scheme", false);

  private final HttpServerAttributesGetter<REQUEST, ?> getter;

  AlternateUrlSchemeProvider(HttpServerAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String apply(REQUEST request) {
    if (!PREFER_FORWARDED_URL_SCHEME) {
      // don't parse headers, extract scheme from the URL
      return null;
    }

    // try Forwarded
    String forwarded = firstHeaderValue(getter.getHttpRequestHeader(request, "forwarded"));
    if (forwarded != null) {
      forwarded = extractProtoFromForwardedHeader(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-Proto
    forwarded = firstHeaderValue(getter.getHttpRequestHeader(request, "x-forwarded-proto"));
    if (forwarded != null) {
      return extractProtoFromForwardedProtoHeader(forwarded);
    }

    return null;
  }
}
