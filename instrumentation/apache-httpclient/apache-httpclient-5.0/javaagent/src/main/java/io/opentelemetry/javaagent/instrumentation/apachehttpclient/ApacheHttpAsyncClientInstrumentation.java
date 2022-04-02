/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.ApacheHttpAsyncClientSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.IOException;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

class ApacheHttpAsyncClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.hc.client5.http.async.HttpAsyncClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.hc.client5.http.async.HttpAsyncClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(5))
            .and(takesArgument(0, named("org.apache.hc.core5.http.nio.AsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.nio.AsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.nio.HandlerFactory")))
            .and(takesArgument(3, named("org.apache.hc.core5.http.protocol.HttpContext")))
            .and(takesArgument(4, named("org.apache.hc.core5.concurrent.FutureCallback"))),
        this.getClass().getName() + "$ClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0, readOnly = false) AsyncRequestProducer requestProducer,
        @Advice.Argument(3) HttpContext httpContext,
        @Advice.Argument(value = 4, readOnly = false) FutureCallback<?> futureCallback) {

      Context parentContext = currentContext();

      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(parentContext, httpContext, futureCallback);
      requestProducer =
          new DelegatingRequestProducer(parentContext, requestProducer, wrappedFutureCallback);
      futureCallback = wrappedFutureCallback;
    }
  }

  public static class DelegatingRequestProducer implements AsyncRequestProducer {
    private final Context parentContext;
    private final AsyncRequestProducer delegate;
    private final WrappedFutureCallback<?> wrappedFutureCallback;

    public DelegatingRequestProducer(
        Context parentContext,
        AsyncRequestProducer delegate,
        WrappedFutureCallback<?> wrappedFutureCallback) {
      this.parentContext = parentContext;
      this.delegate = delegate;
      this.wrappedFutureCallback = wrappedFutureCallback;
    }

    @Override
    public void failed(Exception ex) {
      delegate.failed(ex);
    }

    @Override
    public void sendRequest(RequestChannel channel, HttpContext context)
        throws HttpException, IOException {
      DelegatingRequestChannel requestChannel =
          new DelegatingRequestChannel(channel, parentContext, wrappedFutureCallback);
      delegate.sendRequest(requestChannel, context);
    }

    @Override
    public boolean isRepeatable() {
      return delegate.isRepeatable();
    }

    @Override
    public int available() {
      return delegate.available();
    }

    @Override
    public void produce(DataStreamChannel channel) throws IOException {
      delegate.produce(channel);
    }

    @Override
    public void releaseResources() {
      delegate.releaseResources();
    }
  }

  public static class DelegatingRequestChannel implements RequestChannel {
    private final RequestChannel delegate;
    private final Context parentContext;
    private final WrappedFutureCallback<?> wrappedFutureCallback;

    public DelegatingRequestChannel(
        RequestChannel requestChannel,
        Context parentContext,
        WrappedFutureCallback<?> wrappedFutureCallback) {
      this.delegate = requestChannel;
      this.parentContext = parentContext;
      this.wrappedFutureCallback = wrappedFutureCallback;
    }

    @Override
    public void sendRequest(HttpRequest request, EntityDetails entityDetails, HttpContext context)
        throws HttpException, IOException {
      if (instrumenter().shouldStart(parentContext, request)) {
        wrappedFutureCallback.context = instrumenter().start(parentContext, request);
        wrappedFutureCallback.httpRequest = request;
      }

      delegate.sendRequest(request, entityDetails, context);
    }
  }

  public static class WrappedFutureCallback<T> implements FutureCallback<T> {

    private static final Logger logger = Logger.getLogger(WrappedFutureCallback.class.getName());

    private final Context parentContext;
    private final HttpContext httpContext;
    private final FutureCallback<T> delegate;

    private volatile Context context;
    private volatile HttpRequest httpRequest;

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
        logger.log(FINE, "context was never set");
        completeDelegate(result);
        return;
      }

      instrumenter().end(context, httpRequest, getResponse(httpContext), null);

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
        logger.log(FINE, "context was never set");
        failDelegate(ex);
        return;
      }

      // end span before calling delegate
      instrumenter().end(context, httpRequest, getResponse(httpContext), ex);

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
        logger.log(FINE, "context was never set");
        cancelDelegate();
        return;
      }

      // TODO (trask) add "canceled" span attribute
      // end span before calling delegate
      instrumenter().end(context, httpRequest, getResponse(httpContext), null);

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
