/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.opensearch.v3_0.OpenSearchSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.opensearch.client.transport.Endpoint;

public class OpenSearchTransportInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.opensearch.client.transport.OpenSearchTransport"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("performRequest"))
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, named("org.opensearch.client.transport.Endpoint"))),
        this.getClass().getName() + "$PerformRequestAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("performRequestAsync"))
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, named("org.opensearch.client.transport.Endpoint"))),
        this.getClass().getName() + "$PerformRequestAsyncAdvice");
  }

  public static class AdviceScope {
    private final OpenSearchRequest otelRequest;
    private final Context context;
    private final Scope scope;

    private AdviceScope(OpenSearchRequest otelRequest, Context context, Scope scope) {
      this.otelRequest = otelRequest;
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(Object request, Endpoint<Object, Object, Object> endpoint) {
      Context parentContext = currentContext();
      OpenSearchRequest otelRequest =
          OpenSearchRequest.create(endpoint.method(request), endpoint.requestUrl(request));
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, otelRequest);
      return new AdviceScope(otelRequest, context, context.makeCurrent());
    }

    public CompletableFuture<Object> wrapFuture(CompletableFuture<Object> future) {
      return future.whenComplete(new OpenSearchResponseHandler(context, otelRequest));
    }

    public void endWithResponse(@Nullable Throwable throwable) {
      scope.close();
      instrumenter().end(context, otelRequest, null, throwable);
    }

    public void endWithFuture(@Nullable Throwable throwable) {
      scope.close();
      if (throwable != null) {
        instrumenter().end(context, otelRequest, null, throwable);
      }
      // span ended in OpenSearchResponseHandler
    }
  }

  @SuppressWarnings("unused")
  public static class PerformRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(0) Object request,
        @Advice.Argument(1) Endpoint<Object, Object, Object> endpoint) {
      return AdviceScope.start(request, endpoint);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Return @Nullable Object response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.endWithResponse(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class PerformRequestAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.Argument(0) Object request,
        @Advice.Argument(1) Endpoint<Object, Object, Object> endpoint) {
      AdviceScope adviceScope = AdviceScope.start(request, endpoint);
      if (adviceScope == null) {
        return new Object[] {null};
      }
      return new Object[] {adviceScope};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    @Advice.AssignReturned.ToReturned
    public static CompletableFuture<Object> stopSpan(
        @Advice.Return CompletableFuture<Object> future,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter Object[] enterResult) {
      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.endWithFuture(throwable);
        return adviceScope.wrapFuture(future);
      }
      return future;
    }
  }
}
