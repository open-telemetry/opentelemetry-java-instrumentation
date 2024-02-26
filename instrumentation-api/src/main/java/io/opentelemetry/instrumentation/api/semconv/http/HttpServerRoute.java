/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.HttpRouteState;
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
 * @since 2.0.0
 */
public final class HttpServerRoute {

  /**
   * Returns a {@link ContextCustomizer} that initializes an {@link HttpServerRoute} in the {@link
   * Context} returned from {@link Instrumenter#start(Context, Object)}.
   *
   * @see InstrumenterBuilder#addContextCustomizer(ContextCustomizer)
   */
  public static <REQUEST> ContextCustomizer<REQUEST> create(
      HttpServerAttributesGetter<REQUEST, ?> getter) {
    return builder(getter).build();
  }

  /**
   * Returns a new {@link HttpServerRouteBuilder} that can be used to configure the {@link
   * HttpServerRoute}.
   */
  public static <REQUEST> HttpServerRouteBuilder<REQUEST> builder(
      HttpServerAttributesGetter<REQUEST, ?> getter) {
    return new HttpServerRouteBuilder<>(getter);
  }

  private HttpServerRoute() {}

  /**
   * Updates the {@code http.route} attribute in the received {@code context}.
   *
   * <p>If there is a server span in the context, and the context has been customized with a {@link
   * HttpServerRoute}, then this method will update the route using the provided {@code httpRoute}
   * if and only if the last {@link HttpServerRouteSource} to update the route using this method has
   * strictly lower priority than the provided {@link HttpServerRouteSource}, and the passed value
   * is non-null.
   */
  public static void update(
      Context context, HttpServerRouteSource source, @Nullable String httpRoute) {
    update(context, source, ConstantAdapter.INSTANCE, httpRoute);
  }

  /**
   * Updates the {@code http.route} attribute in the received {@code context}.
   *
   * <p>If there is a server span in the context, and the context has been customized with a {@link
   * HttpServerRoute}, then this method will update the route using the provided {@link
   * HttpServerRouteGetter} if and only if the last {@link HttpServerRouteSource} to update the
   * route using this method has strictly lower priority than the provided {@link
   * HttpServerRouteSource}, and the value returned from the {@link HttpServerRouteGetter} is
   * non-null.
   */
  public static <T> void update(
      Context context,
      HttpServerRouteSource source,
      HttpServerRouteGetter<T> httpRouteGetter,
      T arg1) {
    update(context, source, OneArgAdapter.getInstance(), arg1, httpRouteGetter);
  }

  /**
   * Updates the {@code http.route} attribute in the received {@code context}.
   *
   * <p>If there is a server span in the context, and the context has been customized with a {@link
   * HttpServerRoute}, then this method will update the route using the provided {@link
   * HttpServerRouteBiGetter} if and only if the last {@link HttpServerRouteSource} to update the
   * route using this method has strictly lower priority than the provided {@link
   * HttpServerRouteSource}, and the value returned from the {@link HttpServerRouteBiGetter} is
   * non-null.
   */
  public static <T, U> void update(
      Context context,
      HttpServerRouteSource source,
      HttpServerRouteBiGetter<T, U> httpRouteGetter,
      T arg1,
      U arg2) {
    HttpRouteState httpRouteState = HttpRouteState.fromContextOrNull(context);
    if (httpRouteState == null) {
      return;
    }
    Span serverSpan = httpRouteState.getSpan();
    // even if the server span is not sampled, we have to continue - we need to compute the
    // http.route properly so that it can be captured by the server metrics
    if (serverSpan == null) {
      return;
    }

    // special case for servlet filters, even when we have a route from previous filter see whether
    // the new route is better and if so use it instead
    boolean onlyIfBetterRoute =
        !source.useFirst && source.order == httpRouteState.getUpdatedBySourceOrder();
    if (source.order > httpRouteState.getUpdatedBySourceOrder() || onlyIfBetterRoute) {
      String route = httpRouteGetter.get(context, arg1, arg2);
      if (route != null
          && !route.isEmpty()
          && (!onlyIfBetterRoute || isBetterRoute(httpRouteState, route))) {

        // update just the span name - the attribute will be picked up by the
        // HttpServerAttributesExtractor at the end of request processing
        updateSpanName(serverSpan, httpRouteState, route);

        httpRouteState.update(context, source.order, route);
      }
    }
  }

  // This is used when setting route from a servlet filter to pick the most descriptive (longest)
  // route.
  private static boolean isBetterRoute(HttpRouteState httpRouteState, String name) {
    String route = httpRouteState.getRoute();
    int routeLength = route == null ? 0 : route.length();
    return name.length() > routeLength;
  }

  private static void updateSpanName(Span serverSpan, HttpRouteState httpRouteState, String route) {
    String method = httpRouteState.getMethod();
    // method should never really be null
    serverSpan.updateName(method + " " + route);
  }

  /**
   * Returns the {@code http.route} attribute value that's stored in the {@code context}, or null if
   * it was not set before.
   */
  @Nullable
  static String get(Context context) {
    HttpRouteState httpRouteState = HttpRouteState.fromContextOrNull(context);
    return httpRouteState == null ? null : httpRouteState.getRoute();
  }

  private static final class OneArgAdapter<T>
      implements HttpServerRouteBiGetter<T, HttpServerRouteGetter<T>> {

    private static final OneArgAdapter<Object> INSTANCE = new OneArgAdapter<>();

    @SuppressWarnings("unchecked")
    static <T> OneArgAdapter<T> getInstance() {
      return (OneArgAdapter<T>) INSTANCE;
    }

    @Override
    @Nullable
    public String get(Context context, T arg, HttpServerRouteGetter<T> httpRouteGetter) {
      return httpRouteGetter.get(context, arg);
    }
  }

  private static final class ConstantAdapter implements HttpServerRouteGetter<String> {

    private static final ConstantAdapter INSTANCE = new ConstantAdapter();

    @Nullable
    @Override
    public String get(Context context, String route) {
      return route;
    }
  }
}
