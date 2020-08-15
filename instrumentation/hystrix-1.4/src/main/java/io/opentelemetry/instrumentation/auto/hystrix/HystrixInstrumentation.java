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

package io.opentelemetry.instrumentation.auto.hystrix;

import static io.opentelemetry.instrumentation.auto.hystrix.HystrixDecorator.DECORATE;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.trace.Span.Kind.INTERNAL;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.instrumentation.auto.rxjava.TracedOnSubscribe;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

@AutoService(Instrumenter.class)
public class HystrixInstrumentation extends Instrumenter.Default {

  private static final String OPERATION_NAME = "hystrix.cmd";

  public HystrixInstrumentation() {
    super("hystrix");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("com.netflix.hystrix.HystrixCommand");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(
        namedOneOf(
            "com.netflix.hystrix.HystrixCommand", "com.netflix.hystrix.HystrixObservableCommand"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "rx.__OpenTelemetryTracingUtil",
      "io.opentelemetry.instrumentation.auto.rxjava.SpanFinishingSubscription",
      "io.opentelemetry.instrumentation.auto.rxjava.TracedSubscriber",
      "io.opentelemetry.instrumentation.auto.rxjava.TracedOnSubscribe",
      packageName + ".HystrixDecorator",
      packageName + ".HystrixInstrumentation$HystrixOnSubscribe",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("getExecutionObservable").and(returns(named("rx.Observable"))),
        HystrixInstrumentation.class.getName() + "$ExecuteAdvice");
    transformers.put(
        named("getFallbackObservable").and(returns(named("rx.Observable"))),
        HystrixInstrumentation.class.getName() + "$FallbackAdvice");
    return transformers;
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable result,
        @Advice.Thrown final Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe(result, command, "execute"));
    }
  }

  public static class FallbackAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable<?> result,
        @Advice.Thrown final Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe(result, command, "fallback"));
    }
  }

  public static class HystrixOnSubscribe extends TracedOnSubscribe {
    private final HystrixInvokableInfo<?> command;
    private final String methodName;

    public HystrixOnSubscribe(
        final Observable originalObservable,
        final HystrixInvokableInfo<?> command,
        final String methodName) {
      super(originalObservable, OPERATION_NAME, DECORATE, INTERNAL);

      this.command = command;
      this.methodName = methodName;
    }

    @Override
    protected void afterStart(final Span span) {
      super.afterStart(span);

      DECORATE.onCommand(span, command, methodName);
    }
  }
}
