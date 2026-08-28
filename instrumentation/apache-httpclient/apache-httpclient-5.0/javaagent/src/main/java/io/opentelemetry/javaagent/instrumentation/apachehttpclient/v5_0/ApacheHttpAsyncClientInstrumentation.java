/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.instrumenter;
import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SearchPeerState;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
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
            .and(takesArguments(5))
            .and(takesArgument(0, named("org.apache.hc.core5.http.nio.AsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.nio.AsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.nio.HandlerFactory")))
            .and(takesArgument(3, named("org.apache.hc.core5.http.protocol.HttpContext")))
            .and(takesArgument(4, named("org.apache.hc.core5.concurrent.FutureCallback"))),
        getClass().getName() + "$ClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 1, index = 1),
      @ToArgument(value = 3, index = 2),
      @ToArgument(value = 4, index = 3)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] methodEnter(
        @Advice.Argument(0) AsyncRequestProducer requestProducer,
        @Advice.Argument(1) AsyncResponseConsumer<?> responseConsumer,
        @Advice.Argument(3) @Nullable HttpContext originalHttpContext,
        @Advice.Argument(4) @Nullable FutureCallback<?> futureCallback) {
      HttpContext httpContext = originalHttpContext;

      Context parentContext = currentContext();
      if (httpContext == null) {
        httpContext = new BasicHttpContext();
      }

      boolean captureSearchPeer = SearchPeerState.isActive(parentContext);
      AsyncResponseConsumer<?> modifiedResponseConsumer = responseConsumer;
      if (captureSearchPeer) {
        modifiedResponseConsumer =
            new SearchPeerResponseConsumer<>(parentContext, httpContext, responseConsumer);
      }
      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(
              parentContext, httpContext, futureCallback, captureSearchPeer);
      return new Object[] {
        new DelegatingRequestProducer(parentContext, requestProducer, wrappedFutureCallback),
        modifiedResponseConsumer,
        httpContext,
        wrappedFutureCallback
      };
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

  public static class SearchPeerResponseConsumer<T> implements AsyncResponseConsumer<T> {
    private final Context parentContext;
    private final HttpContext httpContext;
    private final AsyncResponseConsumer<T> delegate;

    public SearchPeerResponseConsumer(
        Context parentContext, HttpContext httpContext, AsyncResponseConsumer<T> delegate) {
      this.parentContext = parentContext;
      this.httpContext = httpContext;
      this.delegate = delegate;
    }

    @Override
    public void consumeResponse(
        HttpResponse response,
        @Nullable EntityDetails entityDetails,
        HttpContext context,
        FutureCallback<T> resultCallback)
        throws HttpException, IOException {
      capture(parentContext, context);
      delegate.consumeResponse(response, entityDetails, context, resultCallback);
    }

    @Override
    public void informationResponse(HttpResponse response, HttpContext context)
        throws HttpException, IOException {
      delegate.informationResponse(response, context);
    }

    @Override
    public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
      delegate.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(ByteBuffer src) throws IOException {
      delegate.consume(src);
    }

    @Override
    public void streamEnd(@Nullable List<? extends Header> trailers)
        throws HttpException, IOException {
      delegate.streamEnd(trailers);
    }

    @Override
    public void failed(Exception cause) {
      capture(parentContext, httpContext);
      delegate.failed(cause);
    }

    @Override
    public void releaseResources() {
      delegate.releaseResources();
    }

    public static void capture(Context parentContext, HttpContext httpContext) {
      EndpointDetails endpointDetails = HttpClientContext.adapt(httpContext).getEndpointDetails();
      if (endpointDetails != null) {
        SearchPeerState.capture(parentContext, endpointDetails.getRemoteAddress());
      }
    }
  }

  public static class WrappedFutureCallback<T> implements FutureCallback<T> {

    private static final Logger logger = Logger.getLogger(WrappedFutureCallback.class.getName());

    private final Context parentContext;
    private final HttpContext httpContext;
    @Nullable private final FutureCallback<T> delegate;
    private final boolean captureSearchPeer;

    @Nullable private volatile Context context;
    @Nullable private volatile HttpRequest httpRequest;

    public WrappedFutureCallback(
        Context parentContext,
        HttpContext httpContext,
        @Nullable FutureCallback<T> delegate,
        boolean captureSearchPeer) {
      this.parentContext = parentContext;
      this.httpContext = httpContext;
      // Note: this can be null in real life, so we have to handle this carefully
      this.delegate = delegate;
      this.captureSearchPeer = captureSearchPeer;
    }

    @Override
    public void completed(T result) {
      if (captureSearchPeer) {
        SearchPeerResponseConsumer.capture(parentContext, httpContext);
      }
      if (context == null) {
        // this is unexpected
        logger.log(FINE, "context was never set");
        completeDelegate(result);
        return;
      }

      instrumenter().end(context, httpRequest, getResponseFromHttpContext(), null);

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
        logger.log(FINE, "context was never set");
        failDelegate(ex);
        return;
      }

      // end span before calling delegate
      instrumenter().end(context, httpRequest, getResponseFromHttpContext(), ex);

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
        logger.log(FINE, "context was never set");
        cancelDelegate();
        return;
      }

      // TODO (trask) add "canceled" span attribute
      // end span before calling delegate
      instrumenter().end(context, httpRequest, getResponseFromHttpContext(), null);

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
