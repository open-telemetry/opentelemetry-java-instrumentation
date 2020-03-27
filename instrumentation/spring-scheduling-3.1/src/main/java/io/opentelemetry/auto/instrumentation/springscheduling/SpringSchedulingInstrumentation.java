/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.springscheduling;

import static io.opentelemetry.auto.instrumentation.springscheduling.SpringSchedulingDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springscheduling.SpringSchedulingDecorator.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class SpringSchedulingInstrumentation extends Instrumenter.Default {

  public SpringSchedulingInstrumentation() {
    super("spring-scheduling");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.scheduling.config.Task");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SpringSchedulingDecorator", getClass().getName() + "$RunnableWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor().and(takesArgument(0, Runnable.class)),
        SpringSchedulingInstrumentation.class.getName() + "$SpringSchedulingAdvice");
  }

  public static class SpringSchedulingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onConstruction(
        @Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
      runnable = RunnableWrapper.wrapIfNeeded(runnable);
    }
  }

  public static class RunnableWrapper implements Runnable {
    private final Runnable runnable;

    private RunnableWrapper(final Runnable runnable) {
      this.runnable = runnable;
    }

    @Override
    public void run() {
      if (runnable == null) {
        return;
      }
      final Span span = TRACER.spanBuilder(DECORATE.spanNameOnRun(runnable)).startSpan();
      DECORATE.afterStart(span);

      try (final Scope scope = currentContextWith(span)) {
        runnable.run();
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        throw throwable;
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
      }
    }

    public static Runnable wrapIfNeeded(final Runnable task) {
      // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
      // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
      if (task instanceof RunnableWrapper) {
        return task;
      }
      return new RunnableWrapper(task);
    }
  }
}
