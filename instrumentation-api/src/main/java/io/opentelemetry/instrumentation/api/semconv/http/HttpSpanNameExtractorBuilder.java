/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A builder of {@link HttpSpanNameExtractor}.
 *
 * @since 2.0.0
 */
public final class HttpSpanNameExtractorBuilder<REQUEST> {

  @Nullable final HttpClientAttributesGetter<REQUEST, ?> clientGetter;
  @Nullable final HttpServerAttributesGetter<REQUEST, ?> serverGetter;
  Set<String> knownMethods = HttpConstants.KNOWN_METHODS;

  public HttpSpanNameExtractorBuilder(
      @Nullable HttpClientAttributesGetter<REQUEST, ?> clientGetter,
      @Nullable HttpServerAttributesGetter<REQUEST, ?> serverGetter) {
    this.clientGetter = clientGetter;
    this.serverGetter = serverGetter;
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
  public HttpSpanNameExtractorBuilder<REQUEST> setKnownMethods(Collection<String> knownMethods) {
    this.knownMethods = new HashSet<>(knownMethods);
    return this;
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
  // don't deprecate this since users will get deprecation warning without a clean way to suppress
  // it if they're using Set
  @CanIgnoreReturnValue
  public HttpSpanNameExtractorBuilder<REQUEST> setKnownMethods(Set<String> knownMethods) {
    return setKnownMethods((Collection<String>) knownMethods);
  }

  /**
   * Returns a new {@link HttpSpanNameExtractor} with the settings of this {@link
   * HttpSpanNameExtractorBuilder}.
   *
   * @see Instrumenter#builder(OpenTelemetry, String, SpanNameExtractor)
   */
  public SpanNameExtractor<REQUEST> build() {
    Set<String> knownMethods = new HashSet<>(this.knownMethods);
    return clientGetter != null
        ? new HttpSpanNameExtractor.Client<>(clientGetter, knownMethods)
        : new HttpSpanNameExtractor.Server<>(requireNonNull(serverGetter), knownMethods);
  }
}
