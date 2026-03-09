/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.url.internal;

import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.semconv.url.UrlAttributesGetter;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalUrlAttributesExtractor<REQUEST> {

  private final UrlAttributesGetter<REQUEST> getter;
  private final Function<REQUEST, String> alternateSchemeProvider;
  private final Set<String> sensitiveQueryParameters;

  public InternalUrlAttributesExtractor(
      UrlAttributesGetter<REQUEST> getter,
      Function<REQUEST, String> alternateSchemeProvider,
      Set<String> sensitiveQueryParameters) {
    this.getter = getter;
    this.alternateSchemeProvider = alternateSchemeProvider;
    this.sensitiveQueryParameters = sensitiveQueryParameters;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    String urlScheme = getUrlScheme(request);
    String urlPath = getter.getUrlPath(request);
    String urlQuery =
        UrlQuerySanitizer.redactQueryString(getter.getUrlQuery(request), sensitiveQueryParameters);

    attributes.put(URL_SCHEME, urlScheme);
    attributes.put(URL_PATH, urlPath);
    attributes.put(URL_QUERY, urlQuery);
  }

  @Nullable
  private String getUrlScheme(REQUEST request) {
    String urlScheme = alternateSchemeProvider.apply(request);
    if (urlScheme == null) {
      urlScheme = getter.getUrlScheme(request);
    }
    return urlScheme;
  }
}
