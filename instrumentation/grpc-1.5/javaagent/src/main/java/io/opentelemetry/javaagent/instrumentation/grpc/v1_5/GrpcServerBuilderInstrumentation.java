/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_5;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.opentelemetry.instrumentation.grpc.v1_5.server.TracingServerInterceptor;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GrpcServerBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.grpc.ServerBuilder");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("io.grpc.ServerBuilder"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("intercept"))
            .and(takesArgument(0, named("io.grpc.ServerInterceptor"))),
        GrpcServerBuilderInstrumentation.class.getName() + "$InterceptAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
        GrpcServerBuilderInstrumentation.class.getName() + "$BuildAdvice");
    return transformers;
  }

  public static class InterceptAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This ServerBuilder<?> serverBuilder,
        @Advice.Argument(0) ServerInterceptor interceptor) {
      // Check against unshaded name.
      if (interceptor
          .getClass()
          .getName()
          .equals("io.opentelemetry.instrumentation.grpc.v1_5.server.TracingServerInterceptor")) {
        @SuppressWarnings("rawtypes")
        ContextStore<ServerBuilder, Boolean> instrumentationContext =
            InstrumentationContext.get(ServerBuilder.class, Boolean.class);
        instrumentationContext.put(serverBuilder, true);
      }
    }
  }

  public static class BuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ServerBuilder<?> serverBuilder) {
      ContextStore<ServerBuilder, Boolean> instrumentationContext =
          InstrumentationContext.get(ServerBuilder.class, Boolean.class);
      if (Boolean.TRUE.equals(instrumentationContext.get(serverBuilder))) {
        return;
      }
      serverBuilder.intercept(TracingServerInterceptor.newInterceptor());
    }
  }
}
