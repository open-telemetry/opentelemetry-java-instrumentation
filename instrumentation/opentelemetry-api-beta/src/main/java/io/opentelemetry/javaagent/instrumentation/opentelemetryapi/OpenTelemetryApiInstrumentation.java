/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import application.io.grpc.Context;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import application.io.opentelemetry.metrics.MeterProvider;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation.ApplicationContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.ApplicationMeterProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerProvider;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class OpenTelemetryApiInstrumentation extends AbstractInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.OpenTelemetry");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(named("getTracerProvider")).and(takesArguments(0)),
        OpenTelemetryApiInstrumentation.class.getName() + "$GetTracerProviderAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(named("getMeterProvider")).and(takesArguments(0)),
        OpenTelemetryApiInstrumentation.class.getName() + "$GetMeterProviderAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(named("getPropagators")).and(takesArguments(0)),
        OpenTelemetryApiInstrumentation.class.getName() + "$GetPropagatorsAdvice");
    return transformers;
  }

  public static class GetTracerProviderAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false)
            application.io.opentelemetry.trace.TracerProvider applicationTracerProvider) {
      ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      applicationTracerProvider =
          new ApplicationTracerProvider(contextStore, applicationTracerProvider);
    }
  }

  public static class GetMeterProviderAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false) MeterProvider applicationMeterProvider) {
      applicationMeterProvider = new ApplicationMeterProvider();
    }
  }

  public static class GetPropagatorsAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false) ContextPropagators applicationContextPropagators) {
      ContextStore<Context, io.grpc.Context> contextStore =
          InstrumentationContext.get(Context.class, io.grpc.Context.class);
      applicationContextPropagators = new ApplicationContextPropagators(contextStore);
    }
  }
}
