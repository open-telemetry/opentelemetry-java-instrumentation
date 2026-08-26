/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1.ApacheHttpAsyncClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SearchPeerState;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

class ApacheHttpAsyncClientInstrumentation implements TypeInstrumentation {

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
        named("execute")
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.nio.protocol.HttpAsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.http.nio.protocol.HttpAsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.http.concurrent.FutureCallback"))),
        getClass().getName() + "$ClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 1, index = 1),
      @ToArgument(value = 2, index = 2),
      @ToArgument(value = 3, index = 3)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) HttpAsyncRequestProducer requestProducer,
        @Advice.Argument(1) HttpAsyncResponseConsumer<?> responseConsumer,
        @Advice.Argument(2) @Nullable HttpContext originalHttpContext,
        @Advice.Argument(3) FutureCallback<?> futureCallback) {

      Context parentContext = currentContext();
      boolean captureSearchPeer = SearchPeerState.isActive(parentContext);
      HttpContext httpContext = originalHttpContext;
      HttpAsyncResponseConsumer<?> modifiedResponseConsumer = responseConsumer;
      if (captureSearchPeer) {
        if (httpContext == null) {
          httpContext = new BasicHttpContext();
        }
        modifiedResponseConsumer =
            new SearchPeerResponseConsumer<>(parentContext, httpContext, responseConsumer);
      }

      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(
              parentContext, httpContext, futureCallback, captureSearchPeer);
      HttpAsyncRequestProducer modifiedRequestProducer =
          new DelegatingRequestProducer(parentContext, requestProducer, wrappedFutureCallback);
      return new Object[] {
        modifiedRequestProducer, modifiedResponseConsumer, httpContext, wrappedFutureCallback
      };
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
        wrappedFutureCallback.otelRequest = otelRequest;
        wrappedFutureCallback.context = instrumenter().start(parentContext, otelRequest);
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

  public static class SearchPeerResponseConsumer<T> implements HttpAsyncResponseConsumer<T> {
    private final Context parentContext;
    private final HttpContext httpContext;
    private final HttpAsyncResponseConsumer<T> delegate;

    public SearchPeerResponseConsumer(
        Context parentContext, HttpContext httpContext, HttpAsyncResponseConsumer<T> delegate) {
      this.parentContext = parentContext;
      this.httpContext = httpContext;
      this.delegate = delegate;
    }

    @Override
    public void responseReceived(HttpResponse response) throws IOException, HttpException {
      capture(parentContext, httpContext);
      delegate.responseReceived(response);
    }

    @Override
    public void consumeContent(ContentDecoder decoder, IOControl ioControl) throws IOException {
      delegate.consumeContent(decoder, ioControl);
    }

    @Override
    public void responseCompleted(HttpContext context) {
      delegate.responseCompleted(context);
    }

    @Override
    public void failed(Exception e) {
      delegate.failed(e);
    }

    @Override
    public Exception getException() {
      return delegate.getException();
    }

    @Override
    public T getResult() {
      return delegate.getResult();
    }

    @Override
    public boolean isDone() {
      return delegate.isDone();
    }

    @Override
    public boolean cancel() {
      return delegate.cancel();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    public static void capture(Context parentContext, @Nullable HttpContext httpContext) {
      if (httpContext == null) {
        return;
      }
      Object connection = HttpCoreContext.adapt(httpContext).getConnection();
      if (!(connection instanceof HttpInetConnection)) {
        return;
      }
      HttpInetConnection inetConnection = (HttpInetConnection) connection;
      if (!inetConnection.isOpen()) {
        return;
      }
      InetAddress remoteAddress = inetConnection.getRemoteAddress();
      int remotePort = inetConnection.getRemotePort();
      if (remoteAddress == null || remotePort < 0) {
        return;
      }
      SearchPeerState.capture(parentContext, new InetSocketAddress(remoteAddress, remotePort));
    }
  }

  public static class WrappedFutureCallback<T> implements FutureCallback<T> {

    private static final Logger logger = Logger.getLogger(WrappedFutureCallback.class.getName());

    private final Context parentContext;
    @Nullable private final HttpContext httpContext;
    @Nullable private final FutureCallback<T> delegate;
    private final boolean captureSearchPeer;

    @Nullable private volatile Context context;
    @Nullable private volatile ApacheHttpClientRequest otelRequest;

    public WrappedFutureCallback(
        Context parentContext,
        @Nullable HttpContext httpContext,
        @Nullable FutureCallback<T> delegate,
        boolean captureSearchPeer) {
      this.parentContext = parentContext;
      this.httpContext = httpContext;
      this.delegate = delegate;
      this.captureSearchPeer = captureSearchPeer;
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

      try (Scope ignored = parentContext.makeCurrent()) {
        completeDelegate(result);
      }
    }

    @Override
    public void failed(Exception ex) {
      if (captureSearchPeer) {
        SearchPeerResponseConsumer.capture(parentContext, httpContext);
      }
      if (context == null) {
        // this is unexpected
        logger.fine("context was never set");
        failDelegate(ex);
        return;
      }

      // end span before calling delegate
      instrumenter().end(context, otelRequest, getResponseFromHttpContext(), ex);

      try (Scope ignored = parentContext.makeCurrent()) {
        failDelegate(ex);
      }
    }

    @Override
    public void cancelled() {
      if (captureSearchPeer) {
        SearchPeerResponseConsumer.capture(parentContext, httpContext);
      }
      if (context == null) {
        // this is unexpected
        logger.fine("context was never set");
        cancelDelegate();
        return;
      }

      // TODO (trask) add "canceled" span attribute
      // end span before calling delegate
      instrumenter().end(context, otelRequest, getResponseFromHttpContext(), null);

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
