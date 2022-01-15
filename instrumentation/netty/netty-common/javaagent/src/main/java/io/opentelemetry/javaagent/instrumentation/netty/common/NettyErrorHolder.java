/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public final class NettyErrorHolder implements ImplicitContextKeyed {

  private static final ContextKey<NettyErrorHolder> KEY = named("opentelemetry-netty-error");

  private volatile Throwable error;

  private NettyErrorHolder() {}

  public static Context init(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new NettyErrorHolder());
  }

  public static void set(Context context, Throwable error) {
    NettyErrorHolder holder = context.get(KEY);
    if (holder != null) {
      holder.error = error;
    }
  }

  @Nullable
  public static Throwable getOrDefault(Context context, @Nullable Throwable error) {
    Throwable result = null;
    NettyErrorHolder holder = context.get(KEY);
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
