/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

public final class RedissonBatchSpanManager {
  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");

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

    activeSpan.request.addCommandsFrom(request);
    activeSpan.updateAttributes();
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

    private void updateAttributes() {
      Span span = Span.fromContext(context);
      if (emitStableDatabaseSemconv()) {
        String operationName = request.getOperationName();
        if (operationName != null) {
          span.updateName(operationName);
        }
        span.setAttribute(DB_OPERATION_NAME, operationName);
        span.setAttribute(DB_QUERY_TEXT, request.getQueryText());
        Long batchSize = request.getOperationBatchSize();
        if (batchSize != null) {
          span.setAttribute(DB_OPERATION_BATCH_SIZE, batchSize);
        }
      }
      if (emitOldDatabaseSemconv()) {
        span.setAttribute(DB_STATEMENT, request.getQueryText());
      }
    }
  }
}
