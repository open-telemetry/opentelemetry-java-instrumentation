/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import feign.Feign;
import feign.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class FeignClientInstrumentation implements TypeInstrumentation {

  private static final String FEIGN_CLIENT = "feign.Client";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(FEIGN_CLIENT);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named(FEIGN_CLIENT));
  }

  @Override
  public void transform(TypeTransformer transformer) {

    transformer.applyAdviceToMethod(
        isMethod().and(named("execute")),
        FeignClientInstrumentation.class.getName() + "$ClientExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("callDepth") CallDepth callDepth, @Advice.Local("scope") Scope scope) {

      callDepth = CallDepth.forClass(Feign.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Return Response response) {

      CallDepth callDepth = CallDepth.forClass(Feign.class);
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      OpenFeignResponseHolder.set(Java8BytecodeBridge.currentContext(), response);
    }
  }
}
