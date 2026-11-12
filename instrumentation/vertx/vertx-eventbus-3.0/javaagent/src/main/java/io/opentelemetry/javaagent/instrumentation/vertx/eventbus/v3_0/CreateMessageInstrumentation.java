/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.propagator;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Injects the current context into the headers of every message the event bus creates. All {@code
 * send}/{@code publish}/{@code request} overloads and {@code MessageProducer}s end up in {@code
 * createMessage}. {@code ClusteredEventBus} is matched too because it overrides {@code
 * createMessage} without calling {@code super}.
 */
class CreateMessageInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.vertx.core.eventbus.impl.EventBusImpl",
        "io.vertx.core.eventbus.impl.clustered.ClusteredEventBus");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> createMessage =
        named("createMessage").and(returns(named("io.vertx.core.eventbus.impl.MessageImpl")));
    // Vert.x 4.5 added an argument before the headers
    transformer.applyAdviceToMethod(
        createMessage.and(takesArgument(2, named("io.vertx.core.MultiMap"))),
        getClass().getName() + "$CreateMessageAdvice");
    transformer.applyAdviceToMethod(
        createMessage.and(takesArgument(3, named("io.vertx.core.MultiMap"))),
        getClass().getName() + "$CreateMessage45Advice");
  }

  public static class MessageHeaders {

    /**
     * The headers of a {@code DeliveryOptions} are handed to the message as-is and the options can
     * be reused. Injecting into them would leave optional propagation fields that a later send
     * doesn't write, like baggage, in place for that send. So the message is created without them,
     * which makes it allocate its own headers, and they are copied over in {@link #inject}.
     *
     * @return the headers to create the message with, and the headers to copy over to it
     */
    public static Object[] isolate(EventBus eventBus, @Nullable MultiMap headers) {
      if (headers == null || NativeTracing.isEnabled(eventBus)) {
        return new Object[] {headers, null};
      }
      return new Object[] {null, headers};
    }

    public static void inject(
        EventBus eventBus, Object returnValue, @Nullable MultiMap headersToCopy) {
      if (!(returnValue instanceof Message) || NativeTracing.isEnabled(eventBus)) {
        return;
      }
      Message<?> message = (Message<?>) returnValue;
      if (headersToCopy != null) {
        message.headers().addAll(headersToCopy);
      }
      propagator().inject(Context.current(), message, MessageHeaderInjector.INSTANCE);
    }

    private MessageHeaders() {}
  }

  @SuppressWarnings("unused")
  public static class CreateMessageAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This EventBus eventBus, @Advice.Argument(2) @Nullable MultiMap headers) {
      return MessageHeaders.isolate(eventBus, headers);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This EventBus eventBus,
        @Advice.Return Object returnValue,
        @Advice.Enter Object[] enter) {
      MessageHeaders.inject(eventBus, returnValue, (MultiMap) enter[1]);
    }
  }

  @SuppressWarnings("unused")
  public static class CreateMessage45Advice {

    @AssignReturned.ToArguments(@ToArgument(value = 3, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This EventBus eventBus, @Advice.Argument(3) @Nullable MultiMap headers) {
      return MessageHeaders.isolate(eventBus, headers);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This EventBus eventBus,
        @Advice.Return Object returnValue,
        @Advice.Enter Object[] enter) {
      MessageHeaders.inject(eventBus, returnValue, (MultiMap) enter[1]);
    }
  }
}
