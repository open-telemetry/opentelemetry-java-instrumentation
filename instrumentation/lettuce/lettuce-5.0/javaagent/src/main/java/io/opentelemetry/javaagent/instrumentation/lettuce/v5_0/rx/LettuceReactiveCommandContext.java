/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import javax.annotation.Nullable;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

public final class LettuceReactiveCommandContext {
  private static final Object HANDLER_KEY = LettuceReactiveCommandContext.class;

  @Nullable
  public static LettuceReactiveCommandHandler handler(CoreSubscriber<?> subscriber) {
    Object value = subscriber.currentContext().getOrDefault(HANDLER_KEY, null);
    return value instanceof LettuceReactiveCommandHandler
        ? (LettuceReactiveCommandHandler) value
        : null;
  }

  public static Context withHandler(Context context, LettuceReactiveCommandHandler handler) {
    return context.put(HANDLER_KEY, handler);
  }

  private LettuceReactiveCommandContext() {}
}
