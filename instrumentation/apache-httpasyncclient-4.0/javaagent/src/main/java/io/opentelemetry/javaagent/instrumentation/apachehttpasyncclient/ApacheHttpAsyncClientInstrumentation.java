/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
        @Advice.Local("otelContext") Context context) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartOperation(parentContext)) {
        return;
      }
      context = tracer().startOperation(parentContext);
      requestProducer = new DelegatingRequestProducer(context, requestProducer);
      futureCallback =
          new TraceContinuedFutureCallback<>(parentContext, context, httpContext, futureCallback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable, @Advice.Local("otelContext") Context context) {
      if (context == null) {
        return;
      }
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      }
    }
  }

  public static class DelegatingRequestProducer implements HttpAsyncRequestProducer {
    Context context;
    HttpAsyncRequestProducer delegate;

    public DelegatingRequestProducer(Context context, HttpAsyncRequestProducer delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public HttpHost getTarget() {
      return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
      HttpRequest request = delegate.generateRequest();
      tracer().inject(context, request);
      tracer().onRequest(context, request);
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
    private final Context parentContext;
    private final Context context;
    private final HttpContext httpContext;
    private final FutureCallback<T> delegate;

    public TraceContinuedFutureCallback(
        Context parentContext,
        Context context,
        HttpContext httpContext,
        FutureCallback<T> delegate) {
      this.parentContext = parentContext;
      this.context = context;
      this.httpContext = httpContext;
      // Note: this can be null in real life, so we have to handle this carefully
      this.delegate = delegate;
    }

    @Override
    public void completed(T result) {
      tracer().end(context, getResponse(httpContext));
      if (delegate != null) {
        try (Scope ignored = parentContext.makeCurrent()) {
          delegate.completed(result);
        }
      }
    }

    @Override
    public void failed(Exception ex) {
      tracer().endExceptionally(context, ex, getResponse(httpContext));
      if (delegate != null) {
        try (Scope ignored = parentContext.makeCurrent()) {
          delegate.failed(ex);
        }
      }
    }

    @Override
    public void cancelled() {
      tracer().end(context, getResponse(httpContext));
      if (delegate != null) {
        try (Scope ignored = parentContext.makeCurrent()) {
          delegate.cancelled();
        }
      }
    }

    private static HttpResponse getResponse(HttpContext context) {
      return (HttpResponse) context.getAttribute(HttpCoreContext.HTTP_RESPONSE);
    }
  }
}
