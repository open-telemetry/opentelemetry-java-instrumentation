/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.OkHttpClient;

public class OkHttp3Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("okhttp3.OkHttpClient$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void trackCallDepth(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(OkHttpClient.Builder.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.This OkHttpClient.Builder builder,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // No-args constructor is automatically called by constructors with args, but we only want to
      // run once from the constructor with args because that is where the dedupe needs to happen.
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (builder.networkInterceptors().contains(OkHttp3Singletons.TRACING_INTERCEPTOR)) {
        return;
      }
      builder.addNetworkInterceptor(OkHttp3Singletons.TRACING_INTERCEPTOR);
    }
  }
}
