/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Helper container for tracking whether instrumentation should update server span name or not. */
public final class ServerSpanNaming {

  private static final ContextKey<ServerSpanNaming> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-span-naming-key");

  public static Context init(Context context, Source initialSource) {
    ServerSpanNaming serverSpanNaming = context.get(CONTEXT_KEY);
    if (serverSpanNaming != null) {
      // TODO (trask) does this ever happen?
      serverSpanNaming.updatedBySource = initialSource;
      return context;
    }
    return context.with(CONTEXT_KEY, new ServerSpanNaming(initialSource));
  }

  private volatile Source updatedBySource;
  // Length of the currently set name. This is used when setting name from a servlet filter
  // to pick the most descriptive (longest) name.
  private volatile int nameLength;

  private ServerSpanNaming(Source initialSource) {
    this.updatedBySource = initialSource;
  }

  /**
   * If there is a server span in the context, and {@link #init(Context, Source)} has been called to
   * populate a {@code ServerSpanName} into the context, then this method will update the server
   * span name using the provided {@link ServerSpanNameSupplier} if and only if the last {@link
   * Source} to update the span name using this method has strictly lower priority than the provided
   * {@link Source}, and the value returned from the {@link ServerSpanNameSupplier} is non-null.
   *
   * <p>If there is a server span in the context, and {@link #init(Context, Source)} has NOT been
   * called to populate a {@code ServerSpanName} into the context, then this method will update the
   * server span name using the provided {@link ServerSpanNameSupplier} if the value returned from
   * it is non-null.
   */
  public static <T> void updateServerSpanName(
      Context context, Source source, ServerSpanNameSupplier<T> serverSpanName, T arg1) {
    updateServerSpanName(context, source, OneArgAdapter.getInstance(), arg1, serverSpanName);
  }

  /**
   * If there is a server span in the context, and {@link #init(Context, Source)} has been called to
   * populate a {@code ServerSpanName} into the context, then this method will update the server
   * span name using the provided {@link ServerSpanNameTwoArgSupplier} if and only if the last
   * {@link Source} to update the span name using this method has strictly lower priority than the
   * provided {@link Source}, and the value returned from the {@link ServerSpanNameTwoArgSupplier}
   * is non-null.
   *
   * <p>If there is a server span in the context, and {@link #init(Context, Source)} has NOT been
   * called to populate a {@code ServerSpanName} into the context, then this method will update the
   * server span name using the provided {@link ServerSpanNameTwoArgSupplier} if the value returned
   * from it is non-null.
   */
  public static <T, U> void updateServerSpanName(
      Context context,
      Source source,
      ServerSpanNameTwoArgSupplier<T, U> serverSpanName,
      T arg1,
      U arg2) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    // checking isRecording() is a helpful optimization for more expensive suppliers
    // (e.g. Spring MVC instrumentation's HandlerAdapterInstrumentation)
    if (serverSpan == null || !serverSpan.isRecording()) {
      return;
    }
    ServerSpanNaming serverSpanNaming = context.get(CONTEXT_KEY);
    if (serverSpanNaming == null) {
      String name = serverSpanName.get(context, arg1, arg2);
      if (name != null && !name.isEmpty()) {
        serverSpan.updateName(name);
      }
      return;
    }
    // special case for servlet filters, even when we have a name from previous filter see whether
    // the new name is better and if so use it instead
    boolean onlyIfBetterName =
        !source.useFirst && source.order == serverSpanNaming.updatedBySource.order;
    if (source.order > serverSpanNaming.updatedBySource.order || onlyIfBetterName) {
      String name = serverSpanName.get(context, arg1, arg2);
      if (name != null
          && !name.isEmpty()
          && (!onlyIfBetterName || serverSpanNaming.isBetterName(name))) {
        serverSpan.updateName(name);
        serverSpanNaming.updatedBySource = source;
        serverSpanNaming.nameLength = name.length();
      }
    }
  }

  private boolean isBetterName(String name) {
    return name.length() > nameLength;
  }

  public enum Source {
    CONTAINER(1),
    // for servlet filters we try to find the best name which isn't necessarily from the first
    // filter that is called
    FILTER(2, /* useFirst= */ false),
    SERVLET(3),
    CONTROLLER(4),
    // Some frameworks, e.g. JaxRS, allow for nested controller/paths and we want to select the
    // longest one
    NESTED_CONTROLLER(5, false);

    private final int order;
    private final boolean useFirst;

    Source(int order) {
      this(order, /* useFirst= */ true);
    }

    Source(int order, boolean useFirst) {
      this.order = order;
      this.useFirst = useFirst;
    }
  }

  private static class OneArgAdapter<T>
      implements ServerSpanNameTwoArgSupplier<T, ServerSpanNameSupplier<T>> {

    private static final OneArgAdapter<Object> INSTANCE = new OneArgAdapter<>();

    @SuppressWarnings("unchecked")
    static <T> OneArgAdapter<T> getInstance() {
      return (OneArgAdapter<T>) INSTANCE;
    }

    @Override
    public @Nullable String get(
        Context context, T arg, ServerSpanNameSupplier<T> serverSpanNameSupplier) {
      return serverSpanNameSupplier.get(context, arg);
    }
  }
}
