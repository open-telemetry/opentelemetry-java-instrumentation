/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.requestInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.sendInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageProducer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Producer spans for {@code MessageProducer}s. From Vert.x 3.9 they don't go through the public
 * {@code EventBus} methods, in Vert.x 3.0 they do and those calls are nested.
 */
class MessageProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.eventbus.impl.MessageProducerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // (event bus or Vertx, address, send, options) in every version
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(4)).and(takesArgument(2, boolean.class)),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        isPublic().and(named("deliveryOptions")).and(takesArguments(1)),
        getClass().getName() + "$DeliveryOptionsAdvice");
    // the producer sends or publishes depending on how it was created
    transformer.applyAdviceToMethod(
        isPublic().and(named("write")), getClass().getName() + "$WriteAdvice");
    // Vert.x 3.9 has send(body) and send(body, replyHandler)
    transformer.applyAdviceToMethod(
        isPublic().and(named("send")).and(takesArguments(1)), getClass().getName() + "$SendAdvice");
    transformer.applyAdviceToMethod(
        isPublic().and(named("send")).and(takesArguments(2)),
        getClass().getName() + "$RequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MessageProducer<?> producer,
        @Advice.Argument(0) Object owner,
        @Advice.Argument(2) boolean send,
        @Advice.Argument(3) @Nullable Object options) {
      MessageProducerState.create(producer, owner, send, options);
    }
  }

  @SuppressWarnings("unused")
  public static class DeliveryOptionsAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This MessageProducer<?> producer, @Advice.Argument(0) @Nullable Object options) {
      MessageProducerState state = MessageProducerState.get(producer);
      if (state != null) {
        state.setOptions(options instanceof DeliveryOptions ? (DeliveryOptions) options : null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ProducerScope onEnter(
        @Advice.This MessageProducer<?> producer,
        // Vert.x 5.2 added write(body, options)
        @Advice.Argument(value = 1, optional = true) @Nullable Object options) {
      return ProducerScope.start(null, producer, options);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter ProducerScope producerScope) {
      producerScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ProducerScope onEnter(@Advice.This MessageProducer<?> producer) {
      return ProducerScope.start(sendInstrumenter(), producer, null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter ProducerScope producerScope) {
      producerScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This MessageProducer<?> producer,
        @Advice.Argument(1) @Nullable Handler<?> replyHandler) {
      MessageProducerState state = MessageProducerState.get(producer);
      Handler<?> handler =
          state == null || state.isNativeTracing()
              ? replyHandler
              : FailureContextHandler.wrap(replyHandler);
      return new Object[] {ProducerScope.start(requestInstrumenter(), producer, null), handler};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enter) {
      ((ProducerScope) enter[0]).end(throwable);
    }
  }
}
