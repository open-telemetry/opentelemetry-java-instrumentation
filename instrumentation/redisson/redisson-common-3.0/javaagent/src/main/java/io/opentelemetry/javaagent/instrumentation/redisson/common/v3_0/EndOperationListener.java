/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

public class EndOperationListener<T> implements BiConsumer<T, Throwable> {
  private final Instrumenter<RedissonRequest, Void> instrumenter;
  private final Context context;
  private final RedissonRequest request;

  public EndOperationListener(
      Instrumenter<RedissonRequest, Void> instrumenter, Context context, RedissonRequest request) {
    this.instrumenter = instrumenter;
    this.context = context;
    this.request = request;
  }

  @Override
  public void accept(@Nullable T unused, @Nullable Throwable error) {
    instrumenter.end(context, request, null, error);
  }
}
