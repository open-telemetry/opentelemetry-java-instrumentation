/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.activejhttp.ActiveJHttpServerConnectionSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This class provides instrumentation for ActiveJ HTTP server connections by applying advice to the
 * {@code serve} method of classes that extend {@code io.activej.http.AsyncServlet}. The
 * instrumentation is designed to integrate with OpenTelemetry for distributed tracing, capturing
 * and propagating trace context through HTTP requests and responses.
 *
 * @author Krishna Chaitanya Surapaneni
 */
public class ActiveJHttpServerConnectionInstrumentation implements TypeInstrumentation {

  /**
   * Matches classes that extend {@code io.activej.http.AsyncServlet} but are not interfaces.
   *
   * @return An {@code ElementMatcher} that identifies target classes for instrumentation.
   */
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("io.activej.http.AsyncServlet")).and(not(isInterface()));
  }

  /**
   * Applies advice to the {@code serve} method of the matched classes. The advice captures trace
   * context at the start of the method and propagates it through the response.
   *
   * @param transformer The {@code TypeTransformer} used to apply the advice.
   */
  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("serve"))
            .and(takesArguments(1).and(takesArgument(0, named("io.activej.http.HttpRequest")))),
        this.getClass().getName() + "$ServeAdvice");
  }

  /**
   * Inner class containing the advice logic for the {@code serve} method. This class defines two
   * methods:
   *
   * <ul>
   *   <li>{@code methodEnter}: Captures the trace context at the start of the method.
   *   <li>{@code methodExit}: Propagates the trace context to the response and ends the span.
   * </ul>
   */
  @SuppressWarnings("unused")
  public static class ServeAdvice {

    /**
     * Advice executed at the start of the {@code serve} method. Captures the current trace context
     * and starts a new span if tracing is enabled for the request.
     *
     * @param asyncServlet The {@code AsyncServlet} instance handling the request.
     * @param request The incoming HTTP request.
     * @param context Local variable to store the OpenTelemetry context.
     * @param scope Local variable to store the OpenTelemetry scope.
     * @param httpRequest Local variable to store the HTTP request.
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This AsyncServlet asyncServlet,
        @Advice.Argument(0) HttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("httpRequest") HttpRequest httpRequest) {
      Context parentContext = currentContext();
      httpRequest = request;
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    /**
     * Advice executed at the end of the {@code serve} method. Propagates the trace context to the
     * response, handles exceptions, and ends the span.
     *
     * @param asyncServlet The {@code AsyncServlet} instance handling the request.
     * @param responsePromise The promise representing the HTTP response.
     * @param throwable Any exception thrown during the execution of the method.
     * @param context Local variable storing the OpenTelemetry context.
     * @param scope Local variable storing the OpenTelemetry scope.
     * @param httpRequest Local variable storing the HTTP request.
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This AsyncServlet asyncServlet,
        @Advice.Return(readOnly = false) Promise<HttpResponse> responsePromise,
        @Advice.Thrown(readOnly = false) Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("httpRequest") HttpRequest httpRequest) {
      if (context == null || scope == null || httpRequest == null) {
        return;
      }
      String traceId = Span.fromContext(context).getSpanContext().getTraceId();
      String spanId = Span.fromContext(context).getSpanContext().getSpanId();
      String traceFlags =
          Span.fromContext(context).getSpanContext().getTraceFlags().asHex().substring(0, 2);
      String traceparent = String.format("00-%s-%s-%s", traceId, spanId, traceFlags);

      scope.close();
      String traceparentHeader = "traceparent";
      if (responsePromise != null) {
        HttpResponse httpResponse = responsePromise.getResult();
        Throwable error = throwable;
        if (responsePromise.isException()) {
          error = responsePromise.getException();
        }
        httpResponse = ActiveJHttpServerHelper.createResponse(error, traceparent, httpResponse);
        instrumenter().end(context, httpRequest, httpResponse, error);
        responsePromise = Promise.of(httpResponse);
      } else if (throwable != null) {
        HttpResponse httpResponse =
            HttpResponse.builder()
                .withCode(500)
                .withPlainText(throwable.getMessage())
                .withHeader(HttpHeaders.of(traceparentHeader), traceparent)
                .build();
        instrumenter().end(context, httpRequest, httpResponse, throwable);
        responsePromise = Promise.of(httpResponse);
        throwable = null;
      } else {
        HttpResponse httpResponse =
            HttpResponse.notFound404()
                .withHeader(HttpHeaders.of(traceparentHeader), traceparent)
                .build();
        instrumenter().end(context, httpRequest, httpResponse, throwable);
        responsePromise = Promise.of(httpResponse);
      }
    }
  }
}
