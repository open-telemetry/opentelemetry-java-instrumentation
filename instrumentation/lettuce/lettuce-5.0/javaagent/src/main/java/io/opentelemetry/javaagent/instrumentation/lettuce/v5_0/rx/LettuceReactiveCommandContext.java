/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import reactor.core.CoreSubscriber;

public final class LettuceReactiveCommandContext {
  static final Object HANDLER_KEY = LettuceReactiveCommandContext.class;

  public static LettuceReactiveCommandHandler handler(CoreSubscriber<?> subscriber) {
    Object value = subscriber.currentContext().getOrDefault(HANDLER_KEY, null);
    return value instanceof LettuceReactiveCommandHandler
        ? (LettuceReactiveCommandHandler) value
        : null;
  }

  private LettuceReactiveCommandContext() {}
}
