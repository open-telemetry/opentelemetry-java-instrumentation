/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.url.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.semconv.url.UrlAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalUrlAttributesExtractor<REQUEST> {

  private final UrlAttributesGetter<REQUEST> getter;
  private final Function<REQUEST, String> alternateSchemeProvider;

  public InternalUrlAttributesExtractor(
      UrlAttributesGetter<REQUEST> getter, Function<REQUEST, String> alternateSchemeProvider) {
    this.getter = getter;
    this.alternateSchemeProvider = alternateSchemeProvider;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    String urlScheme = getUrlScheme(request);
    String urlPath = getter.getUrlPath(request);
    String urlQuery = getter.getUrlQuery(request);

    internalSet(attributes, SemanticAttributes.URL_SCHEME, urlScheme);
    internalSet(attributes, SemanticAttributes.URL_PATH, urlPath);
    internalSet(attributes, SemanticAttributes.URL_QUERY, urlQuery);
  }

  private String getUrlScheme(REQUEST request) {
    String urlScheme = alternateSchemeProvider.apply(request);
    if (urlScheme == null) {
      urlScheme = getter.getUrlScheme(request);
    }
    return urlScheme;
  }
}
