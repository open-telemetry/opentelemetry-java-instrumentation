/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqBatchSendHelper.currentBatchSendState;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8.RocketMqSingletons.batchSendHelper;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8.RocketMqSingletons.sendMessageHook;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqBatchSendHelper.BatchSendState;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;

class RocketMqProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.producer.DefaultMQProducer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("start").and(takesArguments(0)), getClass().getName() + "$StartAdvice");
    ElementMatcher.Junction<MethodDescription> batchSendMethods =
        named("send").and(isPublic()).and(takesArgument(0, named("java.util.Collection")));
    ElementMatcher.Junction<MethodDescription> callbackAtArgumentOne =
        takesArgument(1, named("org.apache.rocketmq.client.producer.SendCallback"));
    ElementMatcher.Junction<MethodDescription> callbackAtArgumentTwo =
        takesArgument(2, named("org.apache.rocketmq.client.producer.SendCallback"));
    transformer.applyAdviceToMethod(
        batchSendMethods.and(not(callbackAtArgumentOne.or(callbackAtArgumentTwo))),
        getClass().getName() + "$BatchSendAdvice");
    transformer.applyAdviceToMethod(
        batchSendMethods.and(callbackAtArgumentOne),
        getClass().getName() + "$AsyncBatchSendArgumentOneAdvice");
    transformer.applyAdviceToMethod(
        batchSendMethods.and(callbackAtArgumentTwo),
        getClass().getName() + "$AsyncBatchSendArgumentTwoAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.FieldValue(value = "defaultMQProducerImpl", declaringType = DefaultMQProducer.class)
            DefaultMQProducerImpl defaultMqProducerImpl) {
      defaultMqProducerImpl.registerSendMessageHook(sendMessageHook());
    }
  }

  @SuppressWarnings("unused")
  public static class BatchSendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.This Object producer) {
      BatchSendState state = batchSendHelper().createBatchSendState(producer, false);
      Object[] enterResult = new Object[] {state, null};
      if (state != null) {
        enterResult[1] = currentBatchSendState().set(state);
      }
      return enterResult;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Object[] enterResult, @Advice.Thrown Throwable throwable) {
      BatchSendState state = (BatchSendState) enterResult[0];
      if (state == null) {
        return;
      }
      currentBatchSendState().restore((BatchSendState) enterResult[1]);
      batchSendHelper().completeBatchSend(state, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncBatchSendArgumentOneAdvice {
    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This Object producer, @Advice.Argument(1) SendCallback callback) {
      BatchSendState state = batchSendHelper().createBatchSendState(producer, callback != null);
      Object[] enterResult = new Object[] {state, null, batchSendHelper().wrap(callback, state)};
      if (state != null) {
        enterResult[1] = currentBatchSendState().set(state);
      }
      return enterResult;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Object[] enterResult, @Advice.Thrown Throwable throwable) {
      BatchSendState state = (BatchSendState) enterResult[0];
      if (state == null) {
        return;
      }
      currentBatchSendState().restore((BatchSendState) enterResult[1]);
      batchSendHelper().completeBatchSend(state, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncBatchSendArgumentTwoAdvice {
    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This Object producer, @Advice.Argument(2) SendCallback callback) {
      BatchSendState state = batchSendHelper().createBatchSendState(producer, callback != null);
      Object[] enterResult = new Object[] {state, null, batchSendHelper().wrap(callback, state)};
      if (state != null) {
        enterResult[1] = currentBatchSendState().set(state);
      }
      return enterResult;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Object[] enterResult, @Advice.Thrown Throwable throwable) {
      BatchSendState state = (BatchSendState) enterResult[0];
      if (state == null) {
        return;
      }
      currentBatchSendState().restore((BatchSendState) enterResult[1]);
      batchSendHelper().completeBatchSend(state, throwable);
    }
  }
}
