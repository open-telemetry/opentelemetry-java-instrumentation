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

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
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
    // send with a reply handler is how a request is made before EventBus.request exists
    ElementMatcher.Junction<MethodDescription> takesReplyHandler =
        takesArgument(2, named(HANDLER_CLASS_NAME)).or(takesArgument(3, named(HANDLER_CLASS_NAME)));

    transformer.applyAdviceToMethod(
        operation.and(named("send")).and(not(takesReplyHandler)),
        getClass().getName() + "$SendAdvice");
    transformer.applyAdviceToMethod(
        operation.and(named("publish")), getClass().getName() + "$PublishAdvice");
    transformer.applyAdviceToMethod(
        operation.and(named("request").or(named("send").and(takesReplyHandler))),
        getClass().getName() + "$RequestAdvice");
  }

  public static class AdviceScope {
    private final CallDepth callDepth;
    @Nullable private final Instrumenter<EventBusRequest, Void> instrumenter;
    @Nullable private final EventBusRequest request;
    @Nullable private final Context context;
    @Nullable private final Scope scope;

    private AdviceScope(
        CallDepth callDepth,
        @Nullable Instrumenter<EventBusRequest, Void> instrumenter,
        @Nullable EventBusRequest request,
        @Nullable Context context,
        @Nullable Scope scope) {
      this.callDepth = callDepth;
      this.instrumenter = instrumenter;
      this.request = request;
      this.context = context;
      this.scope = scope;
    }

    public static AdviceScope start(
        Instrumenter<EventBusRequest, Void> instrumenter,
        EventBus eventBus,
        @Nullable String address,
        @Nullable Object options) {
      CallDepth callDepth = CallDepth.forClass(EventBus.class);
      // send/publish/request overloads delegate to each other, only the outermost is traced
      if (callDepth.getAndIncrement() > 0
          || address == null
          || EventBusInternals.isInternalAddress(address)
          || NativeTracing.isEnabled(eventBus)) {
        return new AdviceScope(callDepth, null, null, null, null);
      }

      // the overloads that take DeliveryOptions all take it as their third argument
      MultiMap headers =
          options instanceof DeliveryOptions ? ((DeliveryOptions) options).getHeaders() : null;
      EventBusRequest request = EventBusRequest.forProducer(address, headers);
      Context parentContext = Context.current();
      if (!instrumenter.shouldStart(parentContext, request)) {
        return new AdviceScope(callDepth, null, null, null, null);
      }
      Context context = instrumenter.start(parentContext, request);
      // CreateMessageInstrumentation runs inside this call and injects the current context
      return new AdviceScope(callDepth, instrumenter, request, context, context.makeCurrent());
    }

    public void end(@Nullable Throwable throwable) {
      if (callDepth.decrementAndGet() > 0
          || instrumenter == null
          || scope == null
          || context == null
          || request == null) {
        return;
      }
      scope.close();
      instrumenter.end(context, request, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(value = 2, optional = true) @Nullable Object options) {
      return AdviceScope.start(sendInstrumenter(), eventBus, address, options);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class PublishAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(value = 2, optional = true) @Nullable Object options) {
      return AdviceScope.start(publishInstrumenter(), eventBus, address, options);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This EventBus eventBus,
        @Advice.Argument(0) @Nullable String address,
        @Advice.Argument(value = 2, optional = true) @Nullable Object options) {
      return AdviceScope.start(requestInstrumenter(), eventBus, address, options);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}
