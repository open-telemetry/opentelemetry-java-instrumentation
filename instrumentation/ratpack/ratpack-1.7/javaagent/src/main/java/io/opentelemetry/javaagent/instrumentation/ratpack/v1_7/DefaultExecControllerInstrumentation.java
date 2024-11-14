/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryExecInitializer;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryExecInterceptor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
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

  @SuppressWarnings("unused")
  public static class SetInitializersAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    public static ImmutableList<? extends ExecInitializer> enter(
        @Advice.Argument(0) ImmutableList<? extends ExecInitializer> initializers) {
      return ImmutableList.<ExecInitializer>builder()
          .addAll(initializers)
          .add(OpenTelemetryExecInitializer.INSTANCE)
          .build();
    }
  }

  @SuppressWarnings("unused")
  public static class SetInterceptorsAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    public static ImmutableList<? extends ExecInterceptor> enter(
        @Advice.Argument(0) ImmutableList<? extends ExecInterceptor> interceptors) {
      return ImmutableList.<ExecInterceptor>builder()
          .addAll(interceptors)
          .add(OpenTelemetryExecInterceptor.INSTANCE)
          .build();
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @SuppressWarnings("UnusedVariable")
    @Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.AssignReturned.ToFields({
      @ToField(value = "initializers", index = 0),
      @ToField(value = "interceptors", index = 1)
    })
    public static Object[] exit(
        @Advice.FieldValue("initializers") ImmutableList<? extends ExecInitializer> initializers,
        @Advice.FieldValue("interceptors") ImmutableList<? extends ExecInterceptor> interceptors) {
      return new Object[] {
        ImmutableList.of(OpenTelemetryExecInitializer.INSTANCE),
        ImmutableList.of(OpenTelemetryExecInterceptor.INSTANCE)
      };
    }
  }
}
