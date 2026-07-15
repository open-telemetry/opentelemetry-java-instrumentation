/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.CommandBatchService;

public class RedissonBatchAdviceScope {
  private static final VirtualField<CommandBatchService, RedissonBatchState> BATCH_STATE_FIELD =
      VirtualField.find(CommandBatchService.class, RedissonBatchState.class);

  private final Instrumenter<RedissonBatchRequest, Void> instrumenter;
  private final RedissonBatchRequest request;
  private final Context context;
  private final Scope scope;

  private RedissonBatchAdviceScope(
      Instrumenter<RedissonBatchRequest, Void> instrumenter,
      RedissonBatchRequest request,
      Context context,
      Context parentContext) {
    this.instrumenter = instrumenter;
    this.request = request;
    this.context = context;
    this.scope = RedissonBatchContext.mark(parentContext).makeCurrent();
  }

  @Nullable
  public static Scope capture(
      CommandBatchService service,
      Object options,
      RedisCommand<?> command,
      Codec codec,
      Object[] parameters) {
    if (!emitStableDatabaseSemconv() || !RedissonBatchState.isAtomic(options)) {
      return null;
    }
    RedissonBatchState state = BATCH_STATE_FIELD.get(service);
    if (state == null) {
      return null;
    }
    if (RedissonBatchContext.isActive(currentContext())) {
      return RedissonBatchContext.startCapture();
    }
    return RedissonBatchContext.startCapture(state, command, codec, parameters);
  }

  @Nullable
  public static Scope captureCandidate(
      CommandBatchService service, RedisCommand<?> command, Codec codec, Object[] parameters) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    RedissonBatchState state = BATCH_STATE_FIELD.get(service);
    if (state == null) {
      return null;
    }
    if (RedissonBatchContext.isActive(currentContext())) {
      return RedissonBatchContext.startCapture();
    }
    return RedissonBatchContext.startCapture(state, command, codec, parameters);
  }

  public static void initialize(CommandBatchService service) {
    if (emitStableDatabaseSemconv() && BATCH_STATE_FIELD.get(service) == null) {
      BATCH_STATE_FIELD.set(service, new RedissonBatchState());
    }
  }

  public static void discard(CommandBatchService service) {
    RedissonBatchState state = BATCH_STATE_FIELD.get(service);
    if (state != null) {
      state.discard();
    }
  }

  @Nullable
  public static RedissonBatchAdviceScope start(
      CommandBatchService service,
      Object options,
      Instrumenter<RedissonBatchRequest, Void> instrumenter) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    RedissonBatchState state = BATCH_STATE_FIELD.get(service);
    if (state == null) {
      return null;
    }
    RedissonBatchRequest request = state.finish(options);
    if (request == null) {
      return null;
    }

    Context parentContext = currentContext();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    Context context = instrumenter.start(parentContext, request);
    return new RedissonBatchAdviceScope(instrumenter, request, context, parentContext);
  }

  public void end(@Nullable CompletionStage<?> result, @Nullable Throwable throwable) {
    scope.close();
    if (throwable != null || result == null) {
      instrumenter.end(context, request, null, throwable);
      return;
    }
    RedissonBatchContext.markFuture(result);
    result.whenComplete((unused, error) -> instrumenter.end(context, request, null, error));
  }
}
