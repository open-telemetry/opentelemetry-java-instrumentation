/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v5_0.ElasticsearchRest5Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestRequest;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.RestResponseListener;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.ResponseListener;

public class RestClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.elasticsearch.client.RestClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(namedOneOf("performRequestAsync", "performRequestAsyncNoCatch"))
            .and(takesArguments(7))
            .and(takesArgument(0, String.class)) // method
            .and(takesArgument(1, String.class)) // endpoint
            .and(takesArgument(5, named("org.elasticsearch.client.ResponseListener"))),
        this.getClass().getName() + "$PerformRequestAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class PerformRequestAsyncAdvice {

    public static class AdviceScope {
      public ElasticsearchRestRequest request;
      public Context context;
      public Scope scope;

      public AdviceScope(ElasticsearchRestRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        if (throwable != null) {
          instrumenter().end(context, request, null, throwable);
        }
        // span ended in RestResponseListener
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 5, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.Argument(0) String method,
        @Advice.Argument(1) String endpoint,
        @Advice.Argument(5) ResponseListener originalResponseListener) {
      ResponseListener responseListener = originalResponseListener;

      Context parentContext = currentContext();
      ElasticsearchRestRequest request = ElasticsearchRestRequest.create(method, endpoint);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return new Object[] {null, responseListener};
      }
      Context context = instrumenter().start(parentContext, request);
      AdviceScope adviceScope = new AdviceScope(request, context, context.makeCurrent());
      responseListener =
          new RestResponseListener(
              responseListener, parentContext, instrumenter(), context, request);
      return new Object[] {adviceScope, responseListener};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable, @Advice.Enter Object[] enterResult) {
      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
