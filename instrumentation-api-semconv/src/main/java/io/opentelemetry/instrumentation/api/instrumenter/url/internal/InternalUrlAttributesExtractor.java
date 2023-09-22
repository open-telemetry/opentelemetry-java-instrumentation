/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.url.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.url.UrlAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalUrlAttributesExtractor<REQUEST> {

  private final UrlAttributesGetter<REQUEST> getter;
  private final Function<REQUEST, String> alternateSchemeProvider;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;

  public InternalUrlAttributesExtractor(
      UrlAttributesGetter<REQUEST> getter,
      Function<REQUEST, String> alternateSchemeProvider,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.alternateSchemeProvider = alternateSchemeProvider;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void onStart(AttributesBuilder attributes, REQUEST request) {
    String urlScheme = getUrlScheme(request);
    String urlPath = getter.getUrlPath(request);
    String urlQuery = getter.getUrlQuery(request);

    if (emitStableUrlAttributes) {
      internalSet(attributes, SemanticAttributes.URL_SCHEME, urlScheme);
      internalSet(attributes, SemanticAttributes.URL_PATH, urlPath);
      internalSet(attributes, SemanticAttributes.URL_QUERY, urlQuery);
    }
    if (emitOldHttpAttributes) {
      internalSet(attributes, SemanticAttributes.HTTP_SCHEME, urlScheme);
      internalSet(attributes, SemanticAttributes.HTTP_TARGET, getTarget(urlPath, urlQuery));
    }
  }

  private String getUrlScheme(REQUEST request) {
    String urlScheme = alternateSchemeProvider.apply(request);
    if (urlScheme == null) {
      urlScheme = getter.getUrlScheme(request);
    }
    return urlScheme;
  }

  @Nullable
  private static String getTarget(@Nullable String path, @Nullable String query) {
    if (path == null && query == null) {
      return null;
    }
    return (path == null ? "" : path) + (query == null || query.isEmpty() ? "" : "?" + query);
  }
}
