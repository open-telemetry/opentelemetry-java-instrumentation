/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.List;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** This instrumentation allows span context propagation across Vert.x reactive executions. */
@AutoService(InstrumentationModule.class)
public class VertxRxInstrumentationModule extends InstrumentationModule {

  public VertxRxInstrumentationModule() {
    super("vertx-reactive", "vertx-reactive-3.5", "vertx");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AsyncResultSingleInstrumentation());
  }

  public static class AsyncResultSingleInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      // Different versions of Vert.x has this class in different packages
      return hasClassesNamed("io.vertx.reactivex.core.impl.AsyncResultSingle")
          .or(hasClassesNamed("io.vertx.reactivex.impl.AsyncResultSingle"));
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("io.vertx.reactivex.core.impl.AsyncResultSingle")
          .or(named("io.vertx.reactivex.impl.AsyncResultSingle"));
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          isConstructor().and(takesArgument(0, named("io.vertx.core.Handler"))),
          VertxRxInstrumentationModule.class.getName() + "$AsyncResultSingleHandlerAdvice");
      transformer.applyAdviceToMethod(
          isConstructor().and(takesArgument(0, Consumer.class)),
          VertxRxInstrumentationModule.class.getName() + "$AsyncResultSingleConsumerAdvice");
    }
  }

  public static class AsyncResultSingleHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Handler<Handler<AsyncResult<?>>> handler) {
      handler =
          AsyncResultHandlerWrapper.wrapIfNeeded(handler, Java8BytecodeBridge.currentContext());
    }
  }

  public static class AsyncResultSingleConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Consumer<Handler<AsyncResult<?>>> handler) {
      handler =
          AsyncResultConsumerWrapper.wrapIfNeeded(handler, Java8BytecodeBridge.currentContext());
    }
  }
}
