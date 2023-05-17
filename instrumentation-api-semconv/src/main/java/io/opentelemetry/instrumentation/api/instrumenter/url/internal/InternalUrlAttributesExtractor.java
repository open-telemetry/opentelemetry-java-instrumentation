/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.url.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.url.UrlAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    String fullUrl = stripSensitiveData(getter.getFullUrl(request));
    String urlScheme = getUrlScheme(request);
    String urlPath = getter.getUrlPath(request);
    String urlQuery = getter.getUrlQuery(request);

    if (emitStableUrlAttributes) {
      internalSet(attributes, UrlAttributes.URL_FULL, fullUrl);
      internalSet(attributes, UrlAttributes.URL_SCHEME, urlScheme);
      internalSet(attributes, UrlAttributes.URL_PATH, urlPath);
      internalSet(attributes, UrlAttributes.URL_QUERY, urlQuery);
    }
    if (emitOldHttpAttributes) {
      internalSet(attributes, SemanticAttributes.HTTP_URL, fullUrl);
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
  private static String stripSensitiveData(@Nullable String url) {
    if (url == null || url.isEmpty()) {
      return url;
    }

    int schemeEndIndex = url.indexOf(':');

    if (schemeEndIndex == -1) {
      // not a valid url
      return url;
    }

    int len = url.length();
    if (len <= schemeEndIndex + 2
        || url.charAt(schemeEndIndex + 1) != '/'
        || url.charAt(schemeEndIndex + 2) != '/') {
      // has no authority component
      return url;
    }

    // look for the end of the authority component:
    //   '/', '?', '#' ==> start of path
    int index;
    int atIndex = -1;
    for (index = schemeEndIndex + 3; index < len; index++) {
      char c = url.charAt(index);

      if (c == '@') {
        atIndex = index;
      }

      if (c == '/' || c == '?' || c == '#') {
        break;
      }
    }

    if (atIndex == -1 || atIndex == len - 1) {
      return url;
    }
    return url.substring(0, schemeEndIndex + 3) + "REDACTED:REDACTED" + url.substring(atIndex);
  }

  @Nullable
  private static String getTarget(@Nullable String path, @Nullable String query) {
    if (path == null && query == null) {
      return null;
    }
    return (path == null ? "" : path) + (query == null || query.isEmpty() ? "" : "?" + query);
  }
}
