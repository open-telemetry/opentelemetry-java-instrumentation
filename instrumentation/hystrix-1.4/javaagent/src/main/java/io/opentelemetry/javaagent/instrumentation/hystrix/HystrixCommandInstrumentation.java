/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.hystrix.HystrixSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.instrumentation.rxjava.v1_0.TracedOnSubscribe;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

public class HystrixCommandInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "com.netflix.hystrix.HystrixCommand", "com.netflix.hystrix.HystrixObservableCommand");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(
        namedOneOf(
            "com.netflix.hystrix.HystrixCommand", "com.netflix.hystrix.HystrixObservableCommand"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getExecutionObservable").and(returns(named("rx.Observable"))),
        this.getClass().getName() + "$ExecuteAdvice");
    transformer.applyAdviceToMethod(
        named("getFallbackObservable").and(returns(named("rx.Observable"))),
        this.getClass().getName() + "$FallbackAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Observable<?> stopSpan(
        @Advice.This HystrixInvokableInfo<?> command,
        @Advice.Return @Nullable Observable<?> result,
        @Advice.Thrown @Nullable Throwable throwable) {

      HystrixRequest request = HystrixRequest.create(command, "execute");
      return Observable.create(new TracedOnSubscribe<>(result, instrumenter(), request));
    }
  }

  @SuppressWarnings("unused")
  public static class FallbackAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Observable<?> stopSpan(
        @Advice.This HystrixInvokableInfo<?> command,
        @Advice.Return @Nullable Observable<?> result,
        @Advice.Thrown @Nullable Throwable throwable) {

      HystrixRequest request = HystrixRequest.create(command, "fallback");
      return Observable.create(new TracedOnSubscribe<>(result, instrumenter(), request));
    }
  }
}
