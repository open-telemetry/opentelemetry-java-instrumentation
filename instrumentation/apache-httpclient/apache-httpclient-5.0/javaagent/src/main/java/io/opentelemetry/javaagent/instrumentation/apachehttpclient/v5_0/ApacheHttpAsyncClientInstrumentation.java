/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics.createOrGetWithParentContext;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientInstrumentationHelper.endInstrumentation;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientInstrumentationHelper.startInstrumentation;
import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.concurrent.FutureCallback;
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

public final class ApacheHttpAsyncClientInstrumentation implements TypeInstrumentation {

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
        @Advice.Argument(value = 1, readOnly = false) AsyncResponseConsumer<?> responseConsumer,
        @Advice.Argument(value = 3, readOnly = false) HttpContext httpContext,
        @Advice.Argument(value = 4, readOnly = false) FutureCallback<?> futureCallback) {

      Context parentContext = currentContext();
      if (httpContext == null) {
        httpContext = new BasicHttpContext();
      }
      HttpOtelContext httpOtelContext = HttpOtelContext.adapt(httpContext);
      httpOtelContext.markAsyncClient();

      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(parentContext, httpContext, futureCallback);
      requestProducer =
          new WrappedRequestProducer(parentContext, requestProducer, wrappedFutureCallback);
      responseConsumer = new WrappedResponseConsumer<>(parentContext, responseConsumer);
      futureCallback = wrappedFutureCallback;
    }
  }

  public static class WrappedResponseConsumer<T> implements AsyncResponseConsumer<T> {
    private final AsyncResponseConsumer<T> delegate;
    private final Context parentContext;

    public WrappedResponseConsumer(Context parentContext, AsyncResponseConsumer<T> delegate) {
      this.parentContext = parentContext;
      this.delegate = delegate;
    }

    @Override
    public void consumeResponse(
        HttpResponse httpResponse,
        EntityDetails entityDetails,
        HttpContext httpContext,
        FutureCallback<T> futureCallback)
        throws HttpException, IOException {
      delegate.consumeResponse(httpResponse, entityDetails, httpContext, futureCallback);
    }

    @Override
    public void informationResponse(HttpResponse httpResponse, HttpContext httpContext)
        throws HttpException, IOException {
      delegate.informationResponse(httpResponse, httpContext);
    }

    @Override
    public void failed(Exception e) {
      delegate.failed(e);
    }

    @Override
    public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
      delegate.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(ByteBuffer byteBuffer) throws IOException {
      if (byteBuffer.hasRemaining()) {
        BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
        metrics.addResponseBytes(byteBuffer.limit());
      }
      delegate.consume(byteBuffer);
    }

    @Override
    public void streamEnd(List<? extends Header> list) throws HttpException, IOException {
      delegate.streamEnd(list);
    }

    @Override
    public void releaseResources() {
      delegate.releaseResources();
    }
  }

  public static class WrappedRequestProducer implements AsyncRequestProducer {
    private final Context parentContext;
    private final AsyncRequestProducer delegate;
    private final WrappedFutureCallback<?> callback;

    public WrappedRequestProducer(
        Context parentContext, AsyncRequestProducer delegate, WrappedFutureCallback<?> callback) {
      this.parentContext = parentContext;
      this.delegate = delegate;
      this.callback = callback;
    }

    @Override
    public void failed(Exception ex) {
      delegate.failed(ex);
    }

    @Override
    public void sendRequest(RequestChannel channel, HttpContext context)
        throws HttpException, IOException {
      RequestChannel requestChannel;
      requestChannel = new WrappedRequestChannel(channel, parentContext, callback);
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
      delegate.produce(new WrappedDataStreamChannel(parentContext, channel));
    }

    @Override
    public void releaseResources() {
      delegate.releaseResources();
    }
  }

  public static class WrappedDataStreamChannel implements DataStreamChannel {
    private final Context parentContext;
    private final DataStreamChannel delegate;

    public WrappedDataStreamChannel(Context parentContext, DataStreamChannel delegate) {
      this.parentContext = parentContext;
      this.delegate = delegate;
    }

    @Override
    public void requestOutput() {
      delegate.requestOutput();
    }

    @Override
    public int write(ByteBuffer byteBuffer) throws IOException {
      BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
      metrics.addRequestBytes(byteBuffer.limit());
      return delegate.write(byteBuffer);
    }

    @Override
    public void endStream() throws IOException {
      delegate.endStream();
    }

    @Override
    public void endStream(List<? extends Header> list) throws IOException {
      delegate.endStream(list);
    }
  }

  public static class WrappedRequestChannel implements RequestChannel {
    private final RequestChannel delegate;
    private final Context parentContext;
    private final WrappedFutureCallback<?> wrappedFutureCallback;

    public WrappedRequestChannel(
        RequestChannel requestChannel,
        Context parentContext,
        WrappedFutureCallback<?> wrappedFutureCallback) {
      this.delegate = requestChannel;
      this.parentContext = parentContext;
      this.wrappedFutureCallback = wrappedFutureCallback;
    }

    @Override
    public void sendRequest(
        HttpRequest request, EntityDetails entityDetails, HttpContext httpContext)
        throws HttpException, IOException {
      ApacheHttpClientRequest otelRequest = new ApacheHttpClientRequest(parentContext, request);
      Context context = startInstrumentation(parentContext, request, otelRequest);

      if (context != null) {
        wrappedFutureCallback.context = context;
        wrappedFutureCallback.otelRequest = otelRequest;

        // As the http processor instrumentation is going to be called asynchronously,
        // we will need to store the otel context variables in http context for the
        // http processor instrumentation to use
        ApacheHttpClientEntityStorage.setCurrentContext(httpContext, context);
      }

      delegate.sendRequest(request, entityDetails, httpContext);
    }
  }

  public static class WrappedFutureCallback<T> implements FutureCallback<T> {
    private static final Logger logger = Logger.getLogger(WrappedFutureCallback.class.getName());

    private final Context parentContext;
    private final HttpContext httpContext;
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
        logger.log(FINE, "context was never set");
        completeDelegate(result);
        return;
      }

      endInstrumentation(context, otelRequest, result, null);

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
      endInstrumentation(context, otelRequest, null, ex);

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
      endInstrumentation(context, otelRequest, null, null);

      if (parentContext == null) {
        cancelDelegate();
        return;
      }

      try (Scope ignored = parentContext.makeCurrent()) {
        cancelDelegate();
      }
    }

    private void completeDelegate(T result) {
      removeOtelAttributes();
      if (delegate != null) {
        delegate.completed(result);
      }
    }

    private void failDelegate(Exception ex) {
      removeOtelAttributes();
      if (delegate != null) {
        delegate.failed(ex);
      }
    }

    private void cancelDelegate() {
      removeOtelAttributes();
      if (delegate != null) {
        delegate.cancelled();
      }
    }

    private void removeOtelAttributes() {
      ApacheHttpClientEntityStorage.clearOtelAttributes(httpContext);
    }
  }
}
