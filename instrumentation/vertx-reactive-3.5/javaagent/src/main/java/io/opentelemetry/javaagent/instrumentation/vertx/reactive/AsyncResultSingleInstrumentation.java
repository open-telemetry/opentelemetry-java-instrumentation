/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** This instrumentation allows span context propagation across Vert.x reactive executions. */
public class AsyncResultSingleInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    // Different versions of Vert.x has this class in different packages
    return hasClassesNamed("io.vertx.reactivex.core.impl.AsyncResultSingle")
        .or(hasClassesNamed("io.vertx.reactivex.impl.AsyncResultSingle"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.vertx.reactivex.core.impl.AsyncResultSingle",
        "io.vertx.reactivex.impl.AsyncResultSingle");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("io.vertx.core.Handler"))),
        this.getClass().getName() + "$ConstructorWithHandlerAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, Consumer.class)),
        this.getClass().getName() + "$ConstructorWithConsumerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Handler<Handler<AsyncResult<?>>> handler) {
      handler =
          AsyncResultHandlerWrapper.wrapIfNeeded(handler, Java8BytecodeBridge.currentContext());
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Consumer<Handler<AsyncResult<?>>> handler) {
      handler =
          AsyncResultConsumerWrapper.wrapIfNeeded(handler, Java8BytecodeBridge.currentContext());
    }
  }
}
