/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.IOException;
import net.bytebuddy.asm.Advice;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.nio.protocol.HttpAsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.http.nio.protocol.HttpAsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.http.concurrent.FutureCallback"))),
        ApacheHttpAsyncClientInstrumentation.class.getName() + "$ClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0, readOnly = false) HttpAsyncRequestProducer requestProducer,
        @Advice.Argument(2) HttpContext httpContext,
        @Advice.Argument(value = 3, readOnly = false) FutureCallback<?> futureCallback) {

      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(parentContext, httpContext, futureCallback);
      requestProducer =
          new DelegatingRequestProducer(parentContext, requestProducer, wrappedFutureCallback);
      futureCallback = wrappedFutureCallback;
    }
  }

  public static class DelegatingRequestProducer implements HttpAsyncRequestProducer {
    private final Context parentContext;
    private final HttpAsyncRequestProducer delegate;
    private final WrappedFutureCallback<?> wrappedFutureCallback;

    public DelegatingRequestProducer(
        Context parentContext,
        HttpAsyncRequestProducer delegate,
        WrappedFutureCallback<?> wrappedFutureCallback) {
      this.parentContext = parentContext;
      this.delegate = delegate;
      this.wrappedFutureCallback = wrappedFutureCallback;
    }

    @Override
    public HttpHost getTarget() {
      return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
      HttpRequest request = delegate.generateRequest();
      wrappedFutureCallback.context = tracer().startSpan(parentContext, request, request);
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

  public static class WrappedFutureCallback<T> implements FutureCallback<T> {

    private static final Logger logger = LoggerFactory.getLogger(WrappedFutureCallback.class);

    private final Context parentContext;
    private final HttpContext httpContext;
    private final FutureCallback<T> delegate;

    private volatile Context context;

    public WrappedFutureCallback(
        Context parentContext, HttpContext httpContext, FutureCallback<T> delegate) {
      this.parentContext = parentContext;
      this.httpContext = httpContext;
      // Note: this can be null in real life, so we have to handle this carefully
      this.delegate = delegate;
    }

    @Override
    public void completed(T result) {
      if (context == null) {
        // this is unexpected
        logger.debug("context was never set");
        completeDelegate(result);
        return;
      }

      tracer().end(context, getResponse(httpContext));

      if (parentContext == null) {
        completeDelegate(result);
        return;
      }

      try (Scope ignored = parentContext.makeCurrent()) {
        completeDelegate(result);
      }
    }

    @Override
    public void failed(Exception ex) {
      if (context == null) {
        // this is unexpected
        logger.debug("context was never set");
        failDelegate(ex);
        return;
      }

      // end span before calling delegate
      tracer().endExceptionally(context, getResponse(httpContext), ex);

      if (parentContext == null) {
        failDelegate(ex);
        return;
      }

      try (Scope ignored = parentContext.makeCurrent()) {
        failDelegate(ex);
      }
    }

    @Override
    public void cancelled() {
      if (context == null) {
        // this is unexpected
        logger.debug("context was never set");
        cancelDelegate();
        return;
      }

      // end span before calling delegate
      tracer().end(context, getResponse(httpContext));

      if (parentContext == null) {
        cancelDelegate();
        return;
      }

      try (Scope ignored = parentContext.makeCurrent()) {
        cancelDelegate();
      }
    }

    private void completeDelegate(T result) {
      if (delegate != null) {
        delegate.completed(result);
      }
    }

    private void failDelegate(Exception ex) {
      if (delegate != null) {
        delegate.failed(ex);
      }
    }

    private void cancelDelegate() {
      if (delegate != null) {
        delegate.cancelled();
      }
    }

    private static HttpResponse getResponse(HttpContext context) {
      return (HttpResponse) context.getAttribute(HttpCoreContext.HTTP_RESPONSE);
    }
  }
}
