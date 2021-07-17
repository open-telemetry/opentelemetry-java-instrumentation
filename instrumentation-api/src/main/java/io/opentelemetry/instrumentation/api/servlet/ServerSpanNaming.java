/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import java.util.function.Supplier;

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
   * span name using the provided {@link Supplier} if and only if the last {@link Source} to update
   * the span name using this method has strictly lower priority than the provided {@link Source},
   * and the value returned from the {@link Supplier} is non-null.
   *
   * <p>If there is a server span in the context, and {@link #init(Context, Source)} has NOT been
   * called to populate a {@code ServerSpanName} into the context, then this method will update the
   * server span name using the provided {@link Supplier} if the value returned from it is non-null.
   */
  public static void updateServerSpanName(
      Context context, Source source, Supplier<String> serverSpanName) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }
    ServerSpanNaming serverSpanNaming = context.get(CONTEXT_KEY);
    if (serverSpanNaming == null) {
      String name = serverSpanName.get();
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
      String name = serverSpanName.get();
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

  // TODO (trask) migrate the one usage (ServletHttpServerTracer) to ServerSpanNaming.init() once we
  // migrate to new Instrumenters (see
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2814#discussion_r617351334
  // for the challenge with doing this now in the current Tracer structure, at least without some
  // bigger changes, which we want to avoid in the Tracers as they are already deprecated)
  @Deprecated
  public static void updateSource(Context context, Source source) {
    ServerSpanNaming serverSpanNaming = context.get(CONTEXT_KEY);
    if (serverSpanNaming != null && source.order > serverSpanNaming.updatedBySource.order) {
      serverSpanNaming.updatedBySource = source;
    }
  }

  public enum Source {
    CONTAINER(1),
    // for servlet filters we try to find the best name which isn't necessarily from the first
    // filter that is called
    FILTER(2, /* useFirst= */ false),
    SERVLET(3),
    CONTROLLER(4);

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
}
