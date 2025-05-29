/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.internal.HttpRouteState;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A builder of {@link HttpServerRoute}.
 *
 * @since 2.0.0
 */
public final class HttpServerRouteBuilder<REQUEST> {

  final HttpServerAttributesGetter<REQUEST, ?> getter;
  Set<String> knownMethods = HttpConstants.KNOWN_METHODS;

  HttpServerRouteBuilder(HttpServerAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  /**
   * Configures the customizer to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this customizer defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>. If an
   * unknown method is encountered, the customizer will use the value {@value HttpConstants#_OTHER}
   * instead.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   */
  @CanIgnoreReturnValue
  public HttpServerRouteBuilder<REQUEST> setKnownMethods(Collection<String> knownMethods) {
    this.knownMethods = new HashSet<>(knownMethods);
    return this;
  }

  /**
   * Configures the customizer to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this customizer defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>. If an
   * unknown method is encountered, the customizer will use the value {@value HttpConstants#_OTHER}
   * instead.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   */
  // don't deprecate this since users will get deprecation warning without a clean way to suppress
  // it if they're using Set
  @CanIgnoreReturnValue
  public HttpServerRouteBuilder<REQUEST> setKnownMethods(Set<String> knownMethods) {
    return setKnownMethods((Collection<String>) knownMethods);
  }

  /**
   * Returns a {@link ContextCustomizer} that initializes an {@link HttpServerRoute} in the {@link
   * Context} returned from {@link Instrumenter#start(Context, Object)}. The returned customizer is
   * configured with the settings of this {@link HttpServerRouteBuilder}.
   *
   * @see InstrumenterBuilder#addContextCustomizer(ContextCustomizer)
   */
  public ContextCustomizer<REQUEST> build() {
    Set<String> knownMethods = new HashSet<>(this.knownMethods);
    return (context, request, startAttributes) -> {
      if (HttpRouteState.fromContextOrNull(context) != null) {
        return context;
      }
      String method = getter.getHttpRequestMethod(request);
      if (method == null || !knownMethods.contains(method)) {
        method = "HTTP";
      }
      return context.with(HttpRouteState.create(method, null, 0));
    };
  }
}
