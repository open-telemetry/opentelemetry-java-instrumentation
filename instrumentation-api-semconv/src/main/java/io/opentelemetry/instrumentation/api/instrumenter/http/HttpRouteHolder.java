/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

/**
 * A helper class that keeps track of the {@code http.route} attribute value during HTTP server
 * request processing.
 *
 * <p>Usually the route is not accessible when the request processing starts; and needs to be set
 * later, after the instrumented operation starts. This class provides several static methods that
 * allow the instrumentation author to provide the matching HTTP route to the instrumentation when
 * it is discovered.
 *
 * @deprecated This class is deprecated and will be removed in the 2.0 release. Use {@link
 *     HttpServerRoute} instead.
 */
@Deprecated
public final class HttpRouteHolder {

  /**
   * Returns a {@link ContextCustomizer} that initializes a {@link HttpRouteHolder} in the {@link
   * Context} returned from {@link Instrumenter#start(Context, Object)}.
   */
  public static <REQUEST> ContextCustomizer<REQUEST> create(
      HttpServerAttributesGetter<REQUEST, ?> getter) {
    return HttpServerRoute.create(getter);
  }

  private HttpRouteHolder() {}

  /**
   * Updates the {@code http.route} attribute in the received {@code context}.
   *
   * <p>If there is a server span in the context, and the context has been customized with a {@link
   * HttpRouteHolder}, then this method will update the route using the provided {@code httpRoute}
   * if and only if the last {@link HttpRouteSource} to update the route using this method has
   * strictly lower priority than the provided {@link HttpRouteSource}, and the passed value is
   * non-null.
   *
   * <p>If there is a server span in the context, and the context has NOT been customized with a
   * {@link HttpRouteHolder}, then this method will update the route using the provided value if it
   * is non-null.
   */
  public static void updateHttpRoute(
      Context context, HttpRouteSource source, @Nullable String httpRoute) {
    HttpServerRoute.update(context, source.toHttpServerRouteSource(), httpRoute);
  }

  /**
   * Updates the {@code http.route} attribute in the received {@code context}.
   *
   * <p>If there is a server span in the context, and the context has been customized with a {@link
   * HttpRouteHolder}, then this method will update the route using the provided {@link
   * HttpRouteGetter} if and only if the last {@link HttpRouteSource} to update the route using this
   * method has strictly lower priority than the provided {@link HttpRouteSource}, and the value
   * returned from the {@link HttpRouteGetter} is non-null.
   *
   * <p>If there is a server span in the context, and the context has NOT been customized with a
   * {@link HttpRouteHolder}, then this method will update the route using the provided {@link
   * HttpRouteGetter} if the value returned from it is non-null.
   */
  public static <T> void updateHttpRoute(
      Context context, HttpRouteSource source, HttpRouteGetter<T> httpRouteGetter, T arg1) {
    HttpServerRoute.update(context, source.toHttpServerRouteSource(), httpRouteGetter, arg1);
  }

  /**
   * Updates the {@code http.route} attribute in the received {@code context}.
   *
   * <p>If there is a server span in the context, and the context has been customized with a {@link
   * HttpRouteHolder}, then this method will update the route using the provided {@link
   * HttpRouteBiGetter} if and only if the last {@link HttpRouteSource} to update the route using
   * this method has strictly lower priority than the provided {@link HttpRouteSource}, and the
   * value returned from the {@link HttpRouteBiGetter} is non-null.
   *
   * <p>If there is a server span in the context, and the context has NOT been customized with a
   * {@code ServerSpanName}, then this method will update the route using the provided {@link
   * HttpRouteBiGetter} if the value returned from it is non-null.
   */
  public static <T, U> void updateHttpRoute(
      Context context,
      HttpRouteSource source,
      HttpRouteBiGetter<T, U> httpRouteGetter,
      T arg1,
      U arg2) {
    HttpServerRoute.update(context, source.toHttpServerRouteSource(), httpRouteGetter, arg1, arg2);
  }
}
