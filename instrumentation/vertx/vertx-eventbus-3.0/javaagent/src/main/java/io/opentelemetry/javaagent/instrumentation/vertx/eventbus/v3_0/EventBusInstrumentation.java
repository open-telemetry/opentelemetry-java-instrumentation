/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.publishInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.requestInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.sendInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class EventBusInstrumentation implements TypeInstrumentation {

  private static final String HANDLER_CLASS_NAME = "io.vertx.core.Handler";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.core.eventbus.EventBus");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.core.eventbus.EventBus"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> operation =
        isPublic().and(takesArgument(0, String.class));
    ElementMatcher.Junction<MethodDescription> replyHandlerThird =
        takesArgument(2, named(HANDLER_CLASS_NAME));
    // the options come before the reply handler
    ElementMatcher.Junction<MethodDescription> replyHandlerFourth =
        takesArgument(3, named(HANDLER_CLASS_NAME));
    ElementMatcher.Junction<MethodDescription> takesReplyHandler =
        replyHandlerThird.or(replyHandlerFourth);
    // send with a reply handler is how a request is made before EventBus.request exists
    ElementMatcher.Junction<MethodDescription> request =
        operation.and(named("request").or(named("send")));

    transformer.applyAdviceToMethod(
        operation.and(named("send")).and(not(takesReplyHandler)),
        getClass().getName() + "$SendAdvice");
    transformer.applyAdviceToMethod(
        operation.and(named("publish")), getClass().getName() + "$PublishAdvice");
    transformer.applyAdviceToMethod(
        operation.and(named("request")).and(not(takesReplyHandler)),
        getClass().getName() + "$RequestAdvice");
    transformer.applyAdviceToMethod(
        request.and(replyHandlerThird), getClass().getName() + "$RequestWithHandlerAdvice");
    transformer.applyAdviceToMethod(
        request.and(replyHandlerFourth),
        getClass().getName() + "$RequestWithOptionsAndHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ProducerScope onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(value = 2, optional = true) @Nullable Object options) {
      return ProducerScope.start(sendInstrumenter(), eventBus, address, options);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter ProducerScope producerScope) {
      producerScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class PublishAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ProducerScope onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(value = 2, optional = true) @Nullable Object options) {
      return ProducerScope.start(publishInstrumenter(), eventBus, address, options);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter ProducerScope producerScope) {
      producerScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ProducerScope onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(value = 2, optional = true) @Nullable Object options) {
      return ProducerScope.start(requestInstrumenter(), eventBus, address, options);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter ProducerScope producerScope) {
      producerScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(2) @Nullable Handler<?> replyHandler) {
      Handler<?> handler =
          NativeTracing.isEnabled(eventBus)
              ? replyHandler
              : FailureContextHandler.wrap(replyHandler);
      return new Object[] {
        ProducerScope.start(requestInstrumenter(), eventBus, address, null), handler
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enter) {
      ((ProducerScope) enter[0]).end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestWithOptionsAndHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 3, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(2) @Nullable Object options,
        @Advice.Argument(3) @Nullable Handler<?> replyHandler) {
      Handler<?> handler =
          NativeTracing.isEnabled(eventBus)
              ? replyHandler
              : FailureContextHandler.wrap(replyHandler);
      return new Object[] {
        ProducerScope.start(requestInstrumenter(), eventBus, address, options), handler
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enter) {
      ((ProducerScope) enter[0]).end(throwable);
    }
  }
}
