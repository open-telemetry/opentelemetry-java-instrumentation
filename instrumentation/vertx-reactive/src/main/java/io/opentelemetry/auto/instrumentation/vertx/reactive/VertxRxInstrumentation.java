/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.vertx.reactive;

import static io.opentelemetry.auto.instrumentation.vertx.reactive.VertxDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** This instrumentation allows span context propagation across Vert.x reactive executions. */
@AutoService(Instrumenter.class)
public class VertxRxInstrumentation extends Instrumenter.Default {

  public VertxRxInstrumentation() {
    super("vertx", "vert.x");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Different versions of Vert.x has this class in different packages
    return hasClassesNamed("io.vertx.reactivex.core.impl.AsyncResultSingle")
        .or(hasClassesNamed("io.vertx.reactivex.impl.AsyncResultSingle"));
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("io.vertx.reactivex.core.impl.AsyncResultSingle")
        .or(named("io.vertx.reactivex.impl.AsyncResultSingle"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AsyncResultConsumerWrapper",
      packageName + ".AsyncResultHandlerWrapper",
      packageName + ".VertxDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> result = new HashMap<>();
    result.put(
        isConstructor().and(takesArgument(0, named("io.vertx.core.Handler"))),
        this.getClass().getName() + "$AsyncResultSingleHandlerAdvice");
    result.put(
        isConstructor().and(takesArgument(0, named("java.util.function.Consumer"))),
        this.getClass().getName() + "$AsyncResultSingleConsumerAdvice");
    return result;
  }

  public static class AsyncResultSingleHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Handler<Handler<AsyncResult<?>>> handler) {
      handler = AsyncResultHandlerWrapper.wrapIfNeeded(handler, TRACER.getCurrentSpan());
    }
  }

  public static class AsyncResultSingleConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Consumer<Handler<AsyncResult<?>>> handler) {
      handler = AsyncResultConsumerWrapper.wrapIfNeeded(handler, TRACER.getCurrentSpan());
    }
  }
}
