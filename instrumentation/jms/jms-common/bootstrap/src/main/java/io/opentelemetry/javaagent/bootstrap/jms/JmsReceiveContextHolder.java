/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public final class JmsReceiveContextHolder implements ImplicitContextKeyed {
  private static final ContextKey<JmsReceiveContextHolder> KEY =
      named("opentelemetry-jms-receive-context");

  private Context receiveContext;

  private JmsReceiveContextHolder() {}

  public static Context init(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new JmsReceiveContextHolder());
  }

  public static void set(Context receiveContext) {
    JmsReceiveContextHolder holder = receiveContext.get(KEY);
    if (holder != null) {
      holder.receiveContext = receiveContext;
    }
  }

  @Nullable
  public static Context getReceiveContext(Context context) {
    JmsReceiveContextHolder holder = context.get(KEY);
    return holder != null ? holder.receiveContext : null;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
