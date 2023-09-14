/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.HashSet;
import java.util.Set;

/** A builder of {@link HttpSpanNameExtractor}. */
public final class HttpSpanNameExtractorBuilder<REQUEST> {

  final HttpCommonAttributesGetter<REQUEST, ?> httpAttributesGetter;
  Set<String> knownMethods = HttpConstants.KNOWN_METHODS;

  HttpSpanNameExtractorBuilder(HttpCommonAttributesGetter<REQUEST, ?> httpAttributesGetter) {
    this.httpAttributesGetter = httpAttributesGetter;
  }

  /**
   * Configures the extractor to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this extractor defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>. If an
   * unknown method is encountered, the extractor will use the value {@value HttpConstants#_OTHER}
   * instead of it and put the original value in an extra {@code http.request.method_original}
   * attribute.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   */
  @CanIgnoreReturnValue
  public HttpSpanNameExtractorBuilder<REQUEST> setKnownMethods(Set<String> knownMethods) {
    this.knownMethods = new HashSet<>(knownMethods);
    return this;
  }

  /**
   * Returns a new {@link HttpSpanNameExtractor} with the settings of this {@link
   * HttpSpanNameExtractorBuilder}.
   */
  public SpanNameExtractor<REQUEST> build() {
    return new HttpSpanNameExtractor<>(this);
  }
}
