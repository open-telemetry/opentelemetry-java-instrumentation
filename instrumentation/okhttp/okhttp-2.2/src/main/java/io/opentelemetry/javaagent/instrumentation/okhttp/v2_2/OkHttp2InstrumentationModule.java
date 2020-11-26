/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OkHttp2InstrumentationModule extends InstrumentationModule {
  public OkHttp2InstrumentationModule() {
    super("okhttp", "okhttp-2.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OkHttpClientInstrumentation());
  }

  public static class OkHttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("com.squareup.okhttp.OkHttpClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(
          isConstructor(), OkHttp2InstrumentationModule.class.getName() + "$OkHttp2ClientAdvice");
    }
  }

  public static class OkHttp2ClientAdvice {
    @Advice.OnMethodExit
    public static void addTracingInterceptor(@Advice.This OkHttpClient client) {
      for (Interceptor interceptor : client.interceptors()) {
        if (interceptor instanceof TracingInterceptor) {
          return;
        }
      }

      client.interceptors().add(new TracingInterceptor());
    }
  }
}
