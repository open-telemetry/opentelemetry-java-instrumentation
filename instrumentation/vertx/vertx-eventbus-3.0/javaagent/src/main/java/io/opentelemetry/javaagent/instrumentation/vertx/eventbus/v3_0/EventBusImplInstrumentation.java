/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Remembers the Vertx instance an event bus belongs to, for {@link NativeTracing}. */
class EventBusImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.eventbus.impl.EventBusImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // every constructor takes the Vertx instance first, ClusteredEventBus passes it to super
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This EventBus eventBus, @Advice.Argument(0) Object vertx) {
      if (vertx instanceof Vertx) {
        NativeTracing.setVertx(eventBus, (Vertx) vertx);
      }
    }
  }
}
