/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.IOException;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
import org.apache.hc.core5.http.protocol.BasicHttpContext;
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
        named("execute")
            .and(takesArgument(0, named("org.apache.hc.core5.http.nio.AsyncRequestProducer")))
            .and(takesArgument(3, named("org.apache.hc.core5.http.protocol.HttpContext")))
            .and(takesArgument(4, named("org.apache.hc.core5.concurrent.FutureCallback"))),
        getClass().getName() + "$ClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 3, index = 1),
      @ToArgument(value = 4, index = 2)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] methodEnter(
        @Advice.Argument(0) AsyncRequestProducer requestProducer,
        @Advice.Argument(3) @Nullable HttpContext originalHttpContext,
        @Advice.Argument(4) @Nullable FutureCallback<?> futureCallback) {
      HttpContext httpContext = originalHttpContext;

      Context parentContext = Context.current();
      if (httpContext == null) {
        httpContext = new BasicHttpContext();
      }

      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(parentContext, httpContext, futureCallback);
      return new Object[] {
        new DelegatingRequestProducer(requestProducer, wrappedFutureCallback),
        httpContext,
        wrappedFutureCallback
      };
    }
  }

  public static class DelegatingRequestProducer implements AsyncRequestProducer {
    private final AsyncRequestProducer delegate;
    private final WrappedFutureCallback<?> wrappedFutureCallback;

    public DelegatingRequestProducer(
        AsyncRequestProducer delegate, WrappedFutureCallback<?> wrappedFutureCallback) {
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
          new DelegatingRequestChannel(channel, wrappedFutureCallback);
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
    private final WrappedFutureCallback<?> wrappedFutureCallback;

    public DelegatingRequestChannel(
        RequestChannel requestChannel, WrappedFutureCallback<?> wrappedFutureCallback) {
      this.delegate = requestChannel;
      this.wrappedFutureCallback = wrappedFutureCallback;
    }

    @Override
    public void sendRequest(HttpRequest request, EntityDetails entityDetails, HttpContext context)
        throws HttpException, IOException {
      wrappedFutureCallback.startSpan(request);

      delegate.sendRequest(request, entityDetails, context);
    }
  }

  public static class WrappedFutureCallback<T> implements FutureCallback<T> {

    private final Context parentContext;
    private final HttpContext httpContext;
    @Nullable private final FutureCallback<T> delegate;

    private final Object lock = new Object();
    // Guarded by lock. Terminal delivery may precede or overlap span creation.
    private boolean startClaimed;
    private boolean completed;
    @Nullable private Context context;
    @Nullable private HttpRequest httpRequest;
    @Nullable private HttpResponse terminalResponse;
    @Nullable private Exception terminalException;

    public WrappedFutureCallback(
        Context parentContext, HttpContext httpContext, @Nullable FutureCallback<T> delegate) {
      this.parentContext = parentContext;
      this.httpContext = httpContext;
      // Note: this can be null in real life, so we have to handle this carefully
      this.delegate = delegate;
    }

    private void startSpan(HttpRequest request) {
      synchronized (lock) {
        if (completed || startClaimed) {
          return;
        }
        startClaimed = true;
      }

      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      Context startedContext = instrumenter().start(parentContext, request);
      HttpResponse response;
      Exception exception;
      synchronized (lock) {
        if (!completed) {
          context = startedContext;
          httpRequest = request;
          return;
        }
        response = terminalResponse;
        exception = terminalException;
      }
      // Completion cannot wait for start: the starter owns ending an unpublished span.
      instrumenter().end(startedContext, request, response, exception);
    }

    private void endSpan(@Nullable Exception exception) {
      HttpResponse response = getResponseFromHttpContext();
      Context startedContext;
      HttpRequest request;
      synchronized (lock) {
        completed = true;
        terminalResponse = response;
        terminalException = exception;
        startedContext = context;
        request = httpRequest;
        context = null;
        httpRequest = null;
      }
      // context is null here when suppressed, not yet claimed, or still being started.
      if (startedContext != null) {
        instrumenter().end(startedContext, request, response, exception);
      }
    }

    @Override
    public void completed(T result) {
      endSpan(null);

      try (Scope ignored = parentContext.makeCurrent()) {
        completeDelegate(result);
      }
    }

    @Override
    public void failed(Exception ex) {
      endSpan(ex);

      try (Scope ignored = parentContext.makeCurrent()) {
        failDelegate(ex);
      }
    }

    @Override
    public void cancelled() {
      // TODO (trask) add "canceled" span attribute
      // end span before calling delegate
      endSpan(null);

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
      if (httpContext instanceof HttpCoreContext) {
        return ((HttpCoreContext) httpContext).getResponse();
      }
      return (HttpResponse) httpContext.getAttribute(HttpCoreContext.HTTP_RESPONSE);
    }
  }
}
