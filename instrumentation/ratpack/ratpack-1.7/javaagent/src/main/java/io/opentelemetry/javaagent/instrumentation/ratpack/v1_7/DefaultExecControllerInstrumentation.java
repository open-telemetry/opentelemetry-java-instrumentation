/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.instrumentation.ratpack.v1_7.OpenTelemetryExecInitializer;
import io.opentelemetry.instrumentation.ratpack.v1_7.OpenTelemetryExecInterceptor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.exec.ExecInitializer;
import ratpack.exec.ExecInterceptor;

public class DefaultExecControllerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ratpack.exec.internal.DefaultExecController");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("setInitializers")
            .and(takesArgument(0, named("com.google.common.collect.ImmutableList"))),
        DefaultExecControllerInstrumentation.class.getName() + "$SetInitializersAdvice");

    transformer.applyAdviceToMethod(
        named("setInterceptors")
            .and(takesArgument(0, named("com.google.common.collect.ImmutableList"))),
        DefaultExecControllerInstrumentation.class.getName() + "$SetInterceptorsAdvice");

    transformer.applyAdviceToMethod(
        isConstructor(),
        DefaultExecControllerInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  public static class SetInitializersAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.Argument(value = 0, readOnly = false)
            ImmutableList<? extends ExecInitializer> initializers) {
      initializers =
          ImmutableList.<ExecInitializer>builder()
              .addAll(initializers)
              .add(OpenTelemetryExecInitializer.INSTANCE)
              .build();
    }
  }

  public static class SetInterceptorsAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.Argument(value = 0, readOnly = false)
            ImmutableList<? extends ExecInterceptor> interceptors) {
      interceptors =
          ImmutableList.<ExecInterceptor>builder()
              .addAll(interceptors)
              .add(OpenTelemetryExecInterceptor.INSTANCE)
              .build();
    }
  }

  public static class ConstructorAdvice {

    @SuppressWarnings("UnusedVariable")
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
        @Advice.FieldValue(value = "initializers", readOnly = false)
            ImmutableList<? extends ExecInitializer> initializers,
        @Advice.FieldValue(value = "interceptors", readOnly = false)
            ImmutableList<? extends ExecInterceptor> interceptors) {
      initializers = ImmutableList.of(OpenTelemetryExecInitializer.INSTANCE);
      interceptors = ImmutableList.of(OpenTelemetryExecInterceptor.INSTANCE);
    }
  }
}
