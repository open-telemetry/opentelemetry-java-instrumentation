/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurefunctions.worker.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.RpcTraceContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * The Azure Functions host passes trace context to the language worker over gRPC, in {@code
 * InvocationRequest.traceContext}, instead of leaving it on the trigger payload. This makes that
 * context current for the duration of the invocation so that telemetry emitted by the function is
 * correlated with the incoming request.
 *
 * <p>See
 * https://github.com/Azure/azure-functions-java-worker/blob/dev/src/main/java/com/microsoft/azure/functions/worker/handler/InvocationRequestHandler.java
 */
class InvocationRequestHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.microsoft.azure.functions.worker.handler.InvocationRequestHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // only the first parameter is matched, the second one is an InvocationResponse.Builder that
    // this instrumentation does not use
    transformer.applyAdviceToMethod(
        named("execute")
            .and(
                takesArgument(
                    0, named("com.microsoft.azure.functions.rpc.messages.InvocationRequest"))),
        getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    public static class AdviceScope {
      private final Scope scope;

      private AdviceScope(Scope scope) {
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(InvocationRequest request) {
        RpcTraceContext traceContext = request.getTraceContext();
        if (traceContext == null) {
          return null;
        }

        Context extractedContext =
            GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.root(), traceContext, RpcTraceContextGetter.INSTANCE);
        SpanContext spanContext = Span.fromContext(extractedContext).getSpanContext();
        if (!spanContext.isValid()) {
          return null;
        }

        // the sampling decision from the host is used as is, it reflects both the decision of the
        // caller that triggered the function and the decision of the host when it starts a new
        // trace
        return new AdviceScope(Context.current().with(Span.wrap(spanContext)).makeCurrent());
      }

      public void end() {
        scope.close();
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.Argument(0) InvocationRequest request) {
      return AdviceScope.start(request);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end();
      }
    }
  }
}
