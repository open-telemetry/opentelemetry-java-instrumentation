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

/**
 * Helper container for tracking whether servlet integration should update server span name or not.
 */
public final class ServerSpanNaming {

  private static final ContextKey<ServerSpanNaming> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-span-naming-key");

  public static Context init(Context context, Source initialSource) {
    if (context.get(CONTEXT_KEY) != null) {
      return context;
    }
    return context.with(CONTEXT_KEY, new ServerSpanNaming(null));
  }

  private volatile Source updatedBySource;

  private ServerSpanNaming(Source initialSource) {
    this.updatedBySource = initialSource;
  }

  public static void updateServerSpanName(
      Context context, Source source, Supplier<String> serverSpanName) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }
    ServerSpanNaming serverSpanNaming = context.get(CONTEXT_KEY);
    if (serverSpanNaming == null) {
      return;
    }
    Source updatedBySource = serverSpanNaming.updatedBySource;
    if (updatedBySource != null && updatedBySource.level >= source.level) {
      return;
    }
    serverSpan.updateName(serverSpanName.get());
    serverSpanNaming.updatedBySource = source;
  }

  public enum Source {
    CONTAINER(1),
    SERVLET(2),
    CONTROLLER(3);

    private final int level;

    Source(int level) {
      this.level = level;
    }
  }
}
