/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientSingletons.instrumenter;
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
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 3, index = 1)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] methodEnter(
        @Advice.Argument(0) HttpAsyncRequestProducer requestProducer,
        @Advice.Argument(2) HttpContext httpContext,
        @Advice.Argument(3) FutureCallback<?> futureCallback) {

      Context parentContext = currentContext();

      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(parentContext, httpContext, futureCallback);
      HttpAsyncRequestProducer modifiedRequestProducer =
          new DelegatingRequestProducer(parentContext, requestProducer, wrappedFutureCallback);
      return new Object[] {modifiedRequestProducer, wrappedFutureCallback};
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
      HttpHost target = delegate.getTarget();
      HttpRequest request = delegate.generateRequest();

      ApacheHttpClientRequest otelRequest = new ApacheHttpClientRequest(target, request);

      if (instrumenter().shouldStart(parentContext, otelRequest)) {
        wrappedFutureCallback.context = instrumenter().start(parentContext, otelRequest);
        wrappedFutureCallback.otelRequest = otelRequest;
      }

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

    private static final Logger logger = Logger.getLogger(WrappedFutureCallback.class.getName());

    private final Context parentContext;
    @Nullable private final HttpContext httpContext;
    private final FutureCallback<T> delegate;

    private volatile Context context;
    private volatile ApacheHttpClientRequest otelRequest;

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
        logger.fine("context was never set");
        completeDelegate(result);
        return;
      }

      instrumenter().end(context, otelRequest, getResponseFromHttpContext(), null);

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
        logger.fine("context was never set");
        failDelegate(ex);
        return;
      }

      // end span before calling delegate
      instrumenter().end(context, otelRequest, getResponseFromHttpContext(), ex);

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
        logger.fine("context was never set");
        cancelDelegate();
        return;
      }

      // TODO (trask) add "canceled" span attribute
      // end span before calling delegate
      instrumenter().end(context, otelRequest, getResponseFromHttpContext(), null);

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

    @Nullable
    private HttpResponse getResponseFromHttpContext() {
      if (httpContext == null) {
        return null;
      }
      return (HttpResponse) httpContext.getAttribute(HttpCoreContext.HTTP_RESPONSE);
    }
  }
}
