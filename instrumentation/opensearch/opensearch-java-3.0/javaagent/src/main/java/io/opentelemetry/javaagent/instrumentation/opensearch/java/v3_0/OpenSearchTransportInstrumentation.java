/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0.OpenSearchJavaSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletableFuture;
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

  @SuppressWarnings("unused")
  public static class PerformRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Object request,
        @Advice.Argument(1) Endpoint<Object, Object, Object> endpoint,
        @Advice.Local("otelRequest") OpenSearchJavaRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = currentContext();
      otelRequest =
          OpenSearchJavaRequest.create(endpoint.method(request), endpoint.requestUrl(request));
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, otelRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") OpenSearchJavaRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }
      scope.close();

      instrumenter().end(context, otelRequest, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class PerformRequestAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Object request,
        @Advice.Argument(value = 1, readOnly = false) Endpoint<Object, Object, Object> endpoint,
        @Advice.Local("otelRequest") OpenSearchJavaRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = currentContext();
      otelRequest =
          OpenSearchJavaRequest.create(endpoint.method(request), endpoint.requestUrl(request));
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, otelRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Return(readOnly = false) CompletableFuture<Object> future,
        @Advice.Local("otelRequest") OpenSearchJavaRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        instrumenter().end(context, otelRequest, null, throwable);
      }

      future.whenComplete(new OpenSearchJavaResponseHandler(context, otelRequest));
    }
  }
}
