/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.io.IOException;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

public class ApacheHttpAsyncClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.http.nio.client.HttpAsyncClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.http.nio.client.HttpAsyncClient"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.nio.protocol.HttpAsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.http.nio.protocol.HttpAsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.http.concurrent.FutureCallback"))),
        ApacheHttpAsyncClientInstrumentation.class.getName() + "$ClientAdvice");
  }

  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0, readOnly = false) HttpAsyncRequestProducer requestProducer,
        @Advice.Argument(2) HttpContext httpContext,
        @Advice.Argument(value = 3, readOnly = false) FutureCallback<?> futureCallback,
        @Advice.Local("otelOperation") HttpClientOperation operation) {
      operation = tracer().startOperation();
      requestProducer = new DelegatingRequestProducer(operation, requestProducer);
      futureCallback = new TraceContinuedFutureCallback<>(operation, httpContext, futureCallback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelOperation") HttpClientOperation operation) {
      if (throwable != null) {
        tracer().endExceptionally(operation, throwable);
      }
    }
  }

  public static class DelegatingRequestProducer implements HttpAsyncRequestProducer {
    HttpClientOperation operation;
    HttpAsyncRequestProducer delegate;

    public DelegatingRequestProducer(
        HttpClientOperation operation, HttpAsyncRequestProducer delegate) {
      this.operation = operation;
      this.delegate = delegate;
    }

    @Override
    public HttpHost getTarget() {
      return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
      HttpRequest request = delegate.generateRequest();
      operation.inject(
          OpenTelemetry.getGlobalPropagators().getTextMapPropagator(),
          request,
          HttpHeadersInjectAdapter.SETTER);
      tracer().onRequest(operation, request);
      return request;
    }

    @Override
    public void produceContent(ContentEncoder encoder, IOControl ioctrl) throws IOException {
      delegate.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted(HttpContext context) {
      delegate.requestCompleted(context);
    }

    @Override
    public void failed(Exception ex) {
      delegate.failed(ex);
    }

    @Override
    public boolean isRepeatable() {
      return delegate.isRepeatable();
    }

    @Override
    public void resetRequest() throws IOException {
      delegate.resetRequest();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  public static class TraceContinuedFutureCallback<T> implements FutureCallback<T> {
    private final HttpClientOperation operation;
    private final HttpContext httpContext;
    private final FutureCallback<T> delegate;

    public TraceContinuedFutureCallback(
        HttpClientOperation operation, HttpContext httpContext, FutureCallback<T> delegate) {
      this.operation = operation;
      this.httpContext = httpContext;
      // Note: this can be null in real life, so we have to handle this carefully
      this.delegate = delegate;
    }

    @Override
    public void completed(T result) {
      tracer().end(operation, getResponse(httpContext));
      if (delegate != null) {
        try (Scope ignored = operation.makeParentCurrent()) {
          delegate.completed(result);
        }
      }
    }

    @Override
    public void failed(Exception ex) {
      tracer().endExceptionally(operation, ex, getResponse(httpContext));
      if (delegate != null) {
        try (Scope ignored = operation.makeParentCurrent()) {
          delegate.failed(ex);
        }
      }
    }

    @Override
    public void cancelled() {
      tracer().end(operation, getResponse(httpContext));
      if (delegate != null) {
        try (Scope ignored = operation.makeParentCurrent()) {
          delegate.cancelled();
        }
      }
    }

    private static HttpResponse getResponse(HttpContext context) {
      return (HttpResponse) context.getAttribute(HttpCoreContext.HTTP_RESPONSE);
    }
  }
}
