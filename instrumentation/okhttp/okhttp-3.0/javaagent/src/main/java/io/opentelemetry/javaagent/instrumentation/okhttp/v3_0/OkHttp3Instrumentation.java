/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
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

  public static class ConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void trackCallDepth(@Advice.Local("callDepth") int callDepth) {
      callDepth = CallDepthThreadLocalMap.incrementCallDepth(OkHttpClient.Builder.class);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.This OkHttpClient.Builder builder, @Advice.Local("callDepth") int callDepth) {
      // No-args constructor is automatically called by constructors with args, but we only want to
      // run once from the constructor with args because that is where the dedupe needs to happen.
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(OkHttpClient.Builder.class);
      if (builder.interceptors().contains(OkHttp3Interceptors.TRACING_INTERCEPTOR)) {
        return;
      }
      builder.addInterceptor(OkHttp3Interceptors.TRACING_INTERCEPTOR);
    }
  }
}
