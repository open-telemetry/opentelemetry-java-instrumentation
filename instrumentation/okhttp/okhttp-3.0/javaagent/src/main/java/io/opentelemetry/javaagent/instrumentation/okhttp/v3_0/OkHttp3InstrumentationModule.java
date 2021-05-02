/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.ArrayList;
import java.util.HashMap;
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

  public static class OkHttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("okhttp3.OkHttpClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isConstructor().and(takesArgument(0, named("okhttp3.OkHttpClient$Builder"))),
          OkHttp3InstrumentationModule.class.getName() + "$OkHttp3ClientConstructorAdvice");
      transformers.put(
          isMethod().and(named("newBuilder").and(returns(named("okhttp3.OkHttpClient$Builder")))),
          OkHttp3InstrumentationModule.class.getName() + "$OkHttp3ClientNewBuilderAdvice");
      return transformers;
    }
  }

  // This advice makes two guarantees:
  // 1) The state of the builder (specifically interceptor list) is the same before and after the
  //    OkHttpClient constructor invocation
  // 2) The interceptor list of the created OkHttpClient has exactly one instance of the tracing
  //    interceptor and it is in the end (assuming no other instrumentations)
  public static class OkHttp3ClientConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.Argument(0) OkHttpClient.Builder builder,
        @Advice.Local("otelOriginalInterceptors") List<Interceptor> originalInterceptors) {

      if (builder.interceptors().contains(OkHttp3Interceptors.TRACING_INTERCEPTOR)) {
        // Potential corner case - the tracing interceptor may be in the builder due to the builder
        // being manually constructed by adding interceptors from an existing client. In this case,
        // save the original interceptors so we can restore them after the constructor call, and
        // then remove all tracing interceptors before adding one as the last.
        originalInterceptors = new ArrayList<>(builder.interceptors());

        while (builder.interceptors().remove(OkHttp3Interceptors.TRACING_INTERCEPTOR)) {}
      }

      builder.addInterceptor(OkHttp3Interceptors.TRACING_INTERCEPTOR);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void removeTracingInterceptor(
        @Advice.Argument(0) OkHttpClient.Builder builder,
        @Advice.Local("otelOriginalInterceptors") List<Interceptor> originalInterceptors) {

      // Restore the interceptor list to what it was before the constructor call. In the common
      // case, a single instance of the tracing interceptor was appended to the end, so it will be
      // removed. For the corner case where builder already contained the tracing interceptor, the
      // original interceptor list was saved to a local and will be restored from there.
      if (originalInterceptors != null) {
        builder.interceptors().clear();
        builder.interceptors().addAll(originalInterceptors);
      } else {
        builder.interceptors().remove(OkHttp3Interceptors.TRACING_INTERCEPTOR);
      }
    }
  }

  public static class OkHttp3ClientNewBuilderAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void removeTracingInterceptor(@Advice.Return OkHttpClient.Builder builder) {
      // Remove the interceptor from the builder returned by newBuilder, as it should be added only
      // by the constructor instrumentation to guarantee that it is the last one in the chain.
      builder.interceptors().remove(OkHttp3Interceptors.TRACING_INTERCEPTOR);
    }
  }
}
