/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.kubernetes.client.openapi.ApiClient;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import okhttp3.Interceptor;

@AutoService(InstrumentationModule.class)
public class KubernetesClientInstrumentationModule extends InstrumentationModule {

  public KubernetesClientInstrumentationModule() {
    super("kubernetes-client", "kubernetes-client-3.0");
  }

  @Override
  protected String[] additionalHelperClassNames() {
    return new String[] {
      "com.google.common.base.Strings",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ApiClientInstrumentation());
  }

  public static class ApiClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("io.kubernetes.client.openapi.ApiClient");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return extendsClass(named("io.kubernetes.client.openapi.ApiClient"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          ElementMatchers.isMethod()
              .and(named("initHttpClient"))
              .and(ElementMatchers.takesArguments(1))
              .and(ElementMatchers.takesArgument(0, named("java.util.List"))),
          KubernetesClientInstrumentationModule.class.getName() + "$KubernetesAdvice");
    }
  }

  public static class KubernetesAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.This ApiClient apiClient, @Advice.Argument(0) List<Interceptor> interceptors) {

      for (Interceptor interceptor : interceptors) {
        if (interceptor instanceof TracingInterceptor) {
          return;
        }
      }

      apiClient.setHttpClient(
          apiClient.getHttpClient().newBuilder().addInterceptor(new TracingInterceptor()).build());
    }
  }
}
