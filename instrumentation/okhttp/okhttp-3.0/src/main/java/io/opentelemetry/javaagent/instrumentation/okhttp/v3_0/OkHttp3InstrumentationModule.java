/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Interceptor;
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

  private static final class OkHttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("okhttp3.OkHttpClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isConstructor().and(takesArgument(0, named("okhttp3.OkHttpClient$Builder"))),
          OkHttp3InstrumentationModule.class.getName() + "$OkHttp3Advice");
    }
  }

  public static class OkHttp3Advice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addTracingInterceptor(@Advice.Argument(0) OkHttpClient.Builder builder) {
      for (Interceptor interceptor : builder.interceptors()) {
        if (interceptor instanceof TracingInterceptor) {
          return;
        }
      }
      TracingInterceptor interceptor = new TracingInterceptor();
      builder.addInterceptor(interceptor);
    }
  }
}
