/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static io.opentelemetry.javaagent.instrumentation.ibmmq.IbmMqSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.ibmmq.IbmMqSingletons.queueManagerIdVirtualField;

import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

public class IbmMqProducerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This Object queue,
      @Advice.Local("otelCallDepth") CallDepth callDepth,
      @Advice.Local("otelRequest") IbmMqRequest request,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelContext") Context context) {

    if (queue == null) {
      return;
    }

    // put(MQMessage) delegates to put(MQMessage, MQPutMessageOptions), and both are matched, so
    // suppress the nested invocation to avoid emitting two spans for one put.
    callDepth = CallDepth.forClass(MQQueue.class);
    if (callDepth.getAndIncrement() > 0) {
      return;
    }

    try {
      String queueName = getQueueName(queue);
      String queueManagerId = getQueueManagerId(queue);

      request = IbmMqRequest.create(queueName, queueManagerId);
      if (!instrumenter().shouldStart(Java8BytecodeBridge.currentContext(), request)) {
        return;
      }

      context = instrumenter().start(Java8BytecodeBridge.currentContext(), request);
      scope = context.makeCurrent();
    } catch (Throwable t) {
      // Suppress
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Local("otelCallDepth") CallDepth callDepth,
      @Advice.Local("otelRequest") IbmMqRequest request,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelContext") Context context,
      @Advice.Thrown @Nullable Throwable throwable) {

    if (callDepth == null || callDepth.decrementAndGet() > 0) {
      return;
    }

    try {
      if (scope != null) {
        scope.close();
      }
      if (request != null && context != null) {
        instrumenter().end(context, request, IbmMqResponse.create(), throwable);
      }
    } catch (Throwable t) {
      // Suppress
    }
  }

  @Nullable
  private static String getQueueName(Object queue) {
    try {
      Object name = queue.getClass().getMethod("getName").invoke(queue);
      return (String) name;
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static String getQueueManagerId(Object queue) {
    try {
      Object queueManager = getParentQueueManager(queue);
      if (!(queueManager instanceof MQQueueManager)) {
        return null;
      }

      VirtualField<MQQueueManager, String> qmIdField = queueManagerIdVirtualField();

      return qmIdField.get((MQQueueManager) queueManager);
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static Object getParentQueueManager(Object queue) {
    try {
      Object queueManager = queue.getClass().getMethod("getQueueManager").invoke(queue);
      return queueManager;
    } catch (Throwable t) {
      return null;
    }
  }
}
