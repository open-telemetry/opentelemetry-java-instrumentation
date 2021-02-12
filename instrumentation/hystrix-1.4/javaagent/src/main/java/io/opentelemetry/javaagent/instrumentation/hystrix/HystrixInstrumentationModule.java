/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.javaagent.instrumentation.hystrix.HystrixTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.rxjava.TracedOnSubscribe;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

@AutoService(InstrumentationModule.class)
public class HystrixInstrumentationModule extends InstrumentationModule {

  private static final String OPERATION_NAME = "hystrix.cmd";

  public HystrixInstrumentationModule() {
    super("hystrix", "hystrix-1.4");
  }

  @Override
  protected String[] additionalHelperClassNames() {
    return new String[] {
      "rx.__OpenTelemetryTracingUtil",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HystrixCommandInstrumentation());
  }

  public static class HystrixCommandInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed(
          "com.netflix.hystrix.HystrixCommand", "com.netflix.hystrix.HystrixObservableCommand");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return extendsClass(
          namedOneOf(
              "com.netflix.hystrix.HystrixCommand",
              "com.netflix.hystrix.HystrixObservableCommand"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          named("getExecutionObservable").and(returns(named("rx.Observable"))),
          HystrixInstrumentationModule.class.getName() + "$ExecuteAdvice");
      transformers.put(
          named("getFallbackObservable").and(returns(named("rx.Observable"))),
          HystrixInstrumentationModule.class.getName() + "$FallbackAdvice");
      return transformers;
    }
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable<?> result,
        @Advice.Thrown Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe<>(result, command, "execute"));
    }
  }

  public static class FallbackAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable<?> result,
        @Advice.Thrown Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe<>(result, command, "fallback"));
    }
  }

  public static class HystrixOnSubscribe<T> extends TracedOnSubscribe<T> {
    private final HystrixInvokableInfo<?> command;
    private final String methodName;

    public HystrixOnSubscribe(
        Observable<T> originalObservable, HystrixInvokableInfo<?> command, String methodName) {
      super(originalObservable, OPERATION_NAME, tracer(), INTERNAL);

      this.command = command;
      this.methodName = methodName;
    }

    @Override
    protected void decorateSpan(Span span) {
      tracer().onCommand(span, command, methodName);
    }
  }
}
