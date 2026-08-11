/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.RocketMqSingletons.simpleConsumerAckInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.ExceptionLogger;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;

public class SimpleConsumerAckOperation {

  private final CallDepth callDepth;
  @Nullable private final RocketMqAckRequest request;
  @Nullable private final Context context;
  @Nullable private final Scope scope;

  @Nullable
  public static SimpleConsumerAckOperation start(
      SimpleConsumer simpleConsumer, MessageView message) {
    if (!emitStableMessagingSemconv()) {
      return null;
    }

    CallDepth callDepth = CallDepth.forClass(SimpleConsumer.class);
    if (callDepth.getAndIncrement() > 0) {
      return new SimpleConsumerAckOperation(callDepth, null, null, null);
    }

    try {
      RocketMqAckRequest request =
          new RocketMqAckRequest(simpleConsumer.getConsumerGroup(), message);
      Context parentContext = Context.current();
      Instrumenter<RocketMqAckRequest, Void> instrumenter = simpleConsumerAckInstrumenter();
      if (!instrumenter.shouldStart(parentContext, request)) {
        return new SimpleConsumerAckOperation(callDepth, null, null, null);
      }

      Context context = instrumenter.start(parentContext, request);
      return new SimpleConsumerAckOperation(callDepth, request, context, context.makeCurrent());
    } catch (Throwable t) {
      callDepth.decrementAndGet();
      ExceptionLogger.logSuppressedError(
          "Error instrumenting RocketMQ SimpleConsumer ack start", t);
      return null;
    }
  }

  private SimpleConsumerAckOperation(
      CallDepth callDepth,
      @Nullable RocketMqAckRequest request,
      @Nullable Context context,
      @Nullable Scope scope) {
    this.callDepth = callDepth;
    this.request = request;
    this.context = context;
    this.scope = scope;
  }

  public void end(@Nullable Throwable error) {
    if (!closeScope() || context == null || request == null) {
      return;
    }
    simpleConsumerAckInstrumenter().end(context, request, null, error);
  }

  public void endAsync(@Nullable CompletableFuture<Void> future, @Nullable Throwable methodError) {
    if (!closeScope() || context == null || request == null) {
      return;
    }
    if (methodError != null || future == null) {
      simpleConsumerAckInstrumenter().end(context, request, null, methodError);
      return;
    }
    future.whenComplete((unused, error) -> endSafely(error));
  }

  private boolean closeScope() {
    if (callDepth.decrementAndGet() > 0) {
      return false;
    }
    if (scope == null || context == null || request == null) {
      return false;
    }
    scope.close();
    return true;
  }

  private void endSafely(@Nullable Throwable error) {
    if (context == null || request == null) {
      return;
    }
    try {
      simpleConsumerAckInstrumenter().end(context, request, null, error);
    } catch (Throwable t) {
      ExceptionLogger.logSuppressedError(
          "Error instrumenting RocketMQ SimpleConsumer ack completion", t);
    }
  }
}
