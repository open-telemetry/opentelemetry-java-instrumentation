/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_36;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.azurecore.v1_36.SuppressNestedClientHelper.disallowNestedClientSpanMono;
import static io.opentelemetry.javaagent.instrumentation.azurecore.v1_36.SuppressNestedClientHelper.disallowNestedClientSpanSync;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.azure.core.http.HttpResponse;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;

public class AzureHttpClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.azure.core.http.HttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("send"))
            .and(takesArgument(1, named("com.azure.core.util.Context")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        this.getClass().getName() + "$SuppressNestedClientMonoAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("sendSync"))
            .and(takesArgument(1, named("com.azure.core.util.Context")))
            .and(returns(named("com.azure.core.http.HttpResponse"))),
        this.getClass().getName() + "$SuppressNestedClientSyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class SuppressNestedClientMonoAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void asyncSendExit(
        @Advice.Argument(1) com.azure.core.util.Context azContext,
        @Advice.Return(readOnly = false) Mono<HttpResponse> mono) {
      mono = disallowNestedClientSpanMono(mono, azContext);
    }
  }

  @SuppressWarnings("unused")
  public static class SuppressNestedClientSyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void syncSendEnter(
        @Advice.Argument(1) com.azure.core.util.Context azContext,
        @Advice.Local("otelScope") Scope scope) {
      scope = disallowNestedClientSpanSync(azContext);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void syncSendExit(
        @Advice.Return HttpResponse response, @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
