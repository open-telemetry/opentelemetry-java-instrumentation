/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.propagator;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Injects the current context into the headers of every message the event bus creates. All {@code
 * send}/{@code publish}/{@code request} overloads and {@code MessageProducer}s end up in {@code
 * createMessage}. Subclasses are matched too because {@code ClusteredEventBus} overrides {@code
 * createMessage} without calling {@code super}.
 */
class CreateMessageInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.vertx.core.eventbus.impl.EventBusImpl"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("createMessage"))
            .and(returns(named("io.vertx.core.eventbus.impl.MessageImpl"))),
        getClass().getName() + "$CreateMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateMessageAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This EventBus eventBus, @Advice.Return Object returnValue) {
      if (!(returnValue instanceof Message) || NativeTracing.isEnabled(eventBus)) {
        return;
      }
      propagator()
          .inject(Context.current(), (Message<?>) returnValue, MessageHeaderInjector.INSTANCE);
    }
  }
}
