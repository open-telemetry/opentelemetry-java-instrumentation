/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.OkHttpClient;

@AutoService(InstrumentationModule.class)
public class OkHttp3InstrumentationModule extends InstrumentationModule {

  public OkHttp3InstrumentationModule() {
    super("okhttp", "okhttp-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OkHttpClientInstrumentation());
  }

  public static class OkHttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("okhttp3.OkHttpClient$Builder");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isConstructor(), OkHttp3InstrumentationModule.class.getName() + "$OkHttp3Advice");
    }
  }

  public static class OkHttp3Advice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void trackCallDepth(@Advice.Local("callDepth") int callDepth) {
      callDepth = CallDepthThreadLocalMap.incrementCallDepth(OkHttpClient.Builder.class);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.This OkHttpClient.Builder builder, @Advice.Local("callDepth") int callDepth) {
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
