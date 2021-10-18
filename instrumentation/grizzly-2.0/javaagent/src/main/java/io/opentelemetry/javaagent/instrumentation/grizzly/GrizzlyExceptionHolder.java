/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public class GrizzlyExceptionHolder implements ImplicitContextKeyed {

  private static final ContextKey<GrizzlyExceptionHolder> KEY =
      named("opentelemetry-grizzly-exception");

  private volatile Throwable error;

  public static Context init(Context context) {
    return context.with(new GrizzlyExceptionHolder());
  }

  public static void set(Context context, Throwable error) {
    GrizzlyExceptionHolder holder = context.get(KEY);
    if (holder != null) {
      holder.error = error;
    }
  }

  @Nullable
  public static Throwable getOrDefault(Context context, @Nullable Throwable error) {
    Throwable result = null;
    GrizzlyExceptionHolder holder = context.get(KEY);
    if (holder != null) {
      result = holder.error;
    }
    return result == null ? error : result;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
