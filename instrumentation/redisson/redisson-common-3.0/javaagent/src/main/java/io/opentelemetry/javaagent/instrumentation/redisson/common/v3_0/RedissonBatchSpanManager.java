/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

public final class RedissonBatchSpanManager {
  private static final Map<Object, ActiveSpan> activeMultiSpans =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static void startMultiSpan(
      Object connection,
      Instrumenter<RedissonRequest, Void> instrumenter,
      Context context,
      RedissonRequest request) {
    activeMultiSpans.put(connection, new ActiveSpan(instrumenter, context, request));
  }

  public static boolean suppressSpanOrEndMultiSpan(
      Object connection, RedissonRequest request, PromiseWrapper<?> promise) {
    ActiveSpan activeSpan = activeMultiSpans.get(connection);
    if (activeSpan == null) {
      return false;
    }

    setEndOperationListener(connection, promise, activeSpan, request.isExecCommand());
    return true;
  }

  public static void endMultiSpan(Object connection, @Nullable Throwable error) {
    ActiveSpan activeSpan = activeMultiSpans.remove(connection);
    if (activeSpan != null) {
      activeSpan.end(error);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"}) // promise value type is irrelevant to span ending
  private static void setEndOperationListener(
      Object connection, PromiseWrapper<?> promise, ActiveSpan activeSpan, boolean endOnSuccess) {
    ((PromiseWrapper) promise)
        .setEndOperationListener(
            new EndOperationListener(
                activeSpan.instrumenter, activeSpan.context, activeSpan.request) {
              @Override
              public void accept(@Nullable Object unused, @Nullable Throwable error) {
                if (endOnSuccess || error != null) {
                  endMultiSpan(connection, error);
                }
              }
            });
  }

  private RedissonBatchSpanManager() {}

  private static final class ActiveSpan {
    private final Instrumenter<RedissonRequest, Void> instrumenter;
    private final Context context;
    private final RedissonRequest request;

    private ActiveSpan(
        Instrumenter<RedissonRequest, Void> instrumenter,
        Context context,
        RedissonRequest request) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.request = request;
    }

    private void end(@Nullable Throwable error) {
      instrumenter.end(context, request, null, error);
    }
  }
}
