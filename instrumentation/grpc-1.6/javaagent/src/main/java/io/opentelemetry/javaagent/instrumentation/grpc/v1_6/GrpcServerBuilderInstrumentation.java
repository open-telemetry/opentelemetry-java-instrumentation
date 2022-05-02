/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.grpc.ServerBuilder;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GrpcServerBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.grpc.ServerBuilder");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.grpc.ServerBuilder"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
        GrpcServerBuilderInstrumentation.class.getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This ServerBuilder<?> serverBuilder,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ServerBuilder.class);
      if (callDepth.getAndIncrement() == 0) {
        VirtualField<ServerBuilder<?>, Boolean> instrumented =
            VirtualField.find(ServerBuilder.class, Boolean.class);
        if (!Boolean.TRUE.equals(instrumented.get(serverBuilder))) {
          serverBuilder.intercept(GrpcSingletons.SERVER_INTERCEPTOR);
          instrumented.set(serverBuilder, true);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This ServerBuilder<?> serverBuilder,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
