/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.server.ServerSpan;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * A helper class that keeps track of the {@code http.route} attribute value during HTTP server
 * request processing.
 *
 * <p>Usually the route is not accessible when the request processing starts; and needs to be set
 * later, after the instrumented operation starts. This class provides several static methods that
 * allow the isntrumentation author to provide the matching HTTP route to the instrumentation when
 * it is discovered.
 */
public final class HttpRouteHolder {

  private static final ContextKey<HttpRouteHolder> CONTEXT_KEY =
      ContextKey.named("opentelemetry-http-server-route-key");

  /**
   * Returns a {@link ContextCustomizer} that initializes a {@link HttpRouteHolder} in the {@link
   * Context} returned from {@link Instrumenter#start(Context, Object)}.
   */
  public static <REQUEST> ContextCustomizer<REQUEST> get() {
    return (context, request, startAttributes) -> {
      if (context.get(CONTEXT_KEY) != null) {
        return context;
      }
      return context.with(CONTEXT_KEY, new HttpRouteHolder());
    };
  }

  private volatile int updatedBySourceOrder = 0;
  @Nullable private volatile String route;

  private HttpRouteHolder() {}

  /**
   * Updates the {@code http.route} attribute in the received {@code context}.
   *
   * <p>If there is a server span in the context, and the context has been customized with a {@link
   * HttpRouteHolder}, then this method will update the route using the provided {@code httpRoute}
   * if and only if the last {@link HttpRouteSource} to update the route using this method has
   * strictly lower priority than the provided {@link HttpRouteSource}, and the pased value is
   * non-null.
   *
   * <p>If there is a server span in the context, and the context has NOT been customized with a
   * {@link HttpRouteHolder}, then this method will update the route using the provided value if it
   * is non-null.
   */
  public static void updateHttpRoute(
      Context context, HttpRouteSource source, @Nullable String httpRoute) {
    updateHttpRoute(context, source, ConstantAdapter.INSTANCE, httpRoute);
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
    updateHttpRoute(context, source, OneArgAdapter.getInstance(), arg1, httpRouteGetter);
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
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    // checking isRecording() is a helpful optimization for more expensive suppliers
    // (e.g. Spring MVC instrumentation's HandlerAdapterInstrumentation)
    if (serverSpan == null || !serverSpan.isRecording()) {
      return;
    }
    HttpRouteHolder httpRouteHolder = context.get(CONTEXT_KEY);
    if (httpRouteHolder == null) {
      String httpRoute = httpRouteGetter.get(context, arg1, arg2);
      if (httpRoute != null && !httpRoute.isEmpty()) {
        updateSpanData(serverSpan, httpRoute);
      }
      return;
    }
    // special case for servlet filters, even when we have a route from previous filter see whether
    // the new route is better and if so use it instead
    boolean onlyIfBetterRoute =
        !source.useFirst && source.order == httpRouteHolder.updatedBySourceOrder;
    if (source.order > httpRouteHolder.updatedBySourceOrder || onlyIfBetterRoute) {
      String route = httpRouteGetter.get(context, arg1, arg2);
      if (route != null
          && !route.isEmpty()
          && (!onlyIfBetterRoute || httpRouteHolder.isBetterRoute(route))) {
        updateSpanData(serverSpan, route);
        httpRouteHolder.updatedBySourceOrder = source.order;
        httpRouteHolder.route = route;
      }
    }
  }

  // TODO: instead of calling setAttribute() consider storing the route in context end retrieving it
  // in the AttributesExtractor
  private static void updateSpanData(Span serverSpan, String route) {
    serverSpan.updateName(route);
    serverSpan.setAttribute(SemanticAttributes.HTTP_ROUTE, route);
  }

  // This is used when setting route from a servlet filter to pick the most descriptive (longest)
  // route.
  private boolean isBetterRoute(String name) {
    String route = this.route;
    int routeLength = route == null ? 0 : route.length();
    return name.length() > routeLength;
  }

  // TODO: use that in HttpServerMetrics
  /**
   * Returns the {@code http.route} attribute value that's stored in the passed {@code context}, or
   * null if it was not set before.
   */
  @Nullable
  public static String getRoute(Context context) {
    HttpRouteHolder httpRouteHolder = context.get(CONTEXT_KEY);
    return httpRouteHolder == null ? null : httpRouteHolder.route;
  }

  private static final class OneArgAdapter<T> implements HttpRouteBiGetter<T, HttpRouteGetter<T>> {

    private static final OneArgAdapter<Object> INSTANCE = new OneArgAdapter<>();

    @SuppressWarnings("unchecked")
    static <T> OneArgAdapter<T> getInstance() {
      return (OneArgAdapter<T>) INSTANCE;
    }

    @Override
    @Nullable
    public String get(Context context, T arg, HttpRouteGetter<T> httpRouteGetter) {
      return httpRouteGetter.get(context, arg);
    }
  }

  private static final class ConstantAdapter implements HttpRouteGetter<String> {

    private static final ConstantAdapter INSTANCE = new ConstantAdapter();

    @Nullable
    @Override
    public String get(Context context, String route) {
      return route;
    }
  }
}
