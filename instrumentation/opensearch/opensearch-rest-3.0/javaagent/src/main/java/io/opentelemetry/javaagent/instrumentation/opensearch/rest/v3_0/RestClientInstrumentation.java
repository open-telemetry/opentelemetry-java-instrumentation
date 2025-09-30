/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v3_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.opensearch.rest.v3_0.OpenSearchRestSingletons.convertResponse;
import static io.opentelemetry.javaagent.instrumentation.opensearch.rest.v3_0.OpenSearchRestSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.OpenSearchRestRequest;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.RestResponseListener;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseListener;

public class RestClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.opensearch.client.RestClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("performRequest"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.opensearch.client.Request"))),
        this.getClass().getName() + "$PerformRequestAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("performRequestAsync"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.opensearch.client.Request")))
            .and(takesArgument(1, named("org.opensearch.client.ResponseListener"))),
        this.getClass().getName() + "$PerformRequestAsyncAdvice");
  }

  public static class AdviceScope {
    private final OpenSearchRestRequest otelRequest;
    private final Context parentContext;
    private final Context context;
    private final Scope scope;

    private AdviceScope(
        OpenSearchRestRequest otelRequest, Context parentContext, Context context, Scope scope) {
      this.otelRequest = otelRequest;
      this.parentContext = parentContext;
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(Request request) {
      Context parentContext = currentContext();
      OpenSearchRestRequest otelRequest =
          OpenSearchRestRequest.create(request.getMethod(), request.getEndpoint());
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, otelRequest);
      return new AdviceScope(otelRequest, parentContext, context, context.makeCurrent());
    }

    public ResponseListener wrapListener(ResponseListener responseListener) {
      return new RestResponseListener(
          responseListener,
          parentContext,
          instrumenter(),
          context,
          otelRequest,
          OpenSearchRestSingletons::convertResponse);
    }

    public void endSync(@Nullable Response response, @Nullable Throwable throwable) {
      scope.close();
      instrumenter().end(context, otelRequest, convertResponse(response), throwable);
    }

    public void endAsync(@Nullable Throwable throwable) {
      if (throwable != null) {
        instrumenter().end(context, otelRequest, null, throwable);
      }
      // span ended in RestResponseListener
    }
  }

  @SuppressWarnings("unused")
  public static class PerformRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) Request request) {
      return AdviceScope.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Return @Nullable Response response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.endSync(response, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class PerformRequestAsyncAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) ResponseListener originalResponseListener) {
      AdviceScope adviceScope = AdviceScope.start(request);
      if (adviceScope == null) {
        return new Object[] {null, originalResponseListener};
      }
      return new Object[] {adviceScope, adviceScope.wrapListener(originalResponseListener)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enterResult) {
      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.endAsync(throwable);
      }
    }
  }
}
