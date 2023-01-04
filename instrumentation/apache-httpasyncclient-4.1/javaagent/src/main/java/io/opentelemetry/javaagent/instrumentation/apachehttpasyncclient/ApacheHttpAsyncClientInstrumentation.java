/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics.createOrGetWithParentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.ApacheHttpClientInternalEntityStorage;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.ApacheHttpClientRequest;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.HttpOtelContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
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

public final class ApacheHttpAsyncClientInstrumentation implements TypeInstrumentation {

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
        @Advice.Argument(value = 1, readOnly = false) HttpAsyncResponseConsumer<?> responseConsumer,
        @Advice.Argument(value = 2, readOnly = false) HttpContext httpContext,
        @Advice.Argument(value = 3, readOnly = false) FutureCallback<?> futureCallback) {

      Context parentContext = currentContext();
      if (httpContext == null) {
        httpContext = new BasicHttpContext();
      }
      HttpOtelContext httpOtelContext = HttpOtelContext.adapt(httpContext);
      httpOtelContext.markAsyncClient();

      WrappedFutureCallback<?> wrappedFutureCallback =
          new WrappedFutureCallback<>(parentContext, httpOtelContext, futureCallback);
      requestProducer =
          new WrappedRequestProducer(
              parentContext, httpOtelContext, requestProducer, wrappedFutureCallback);
      responseConsumer = new WrappedResponseConsumer<>(parentContext, responseConsumer);
      futureCallback = wrappedFutureCallback;
    }
  }

  public static class WrappedResponseConsumer<T> implements HttpAsyncResponseConsumer<T> {
    private final Context parentContext;
    private final HttpAsyncResponseConsumer<T> delegate;

    public WrappedResponseConsumer(Context parentContext, HttpAsyncResponseConsumer<T> delegate) {
      this.parentContext = parentContext;
      this.delegate = delegate;
    }

    @Override
    public void responseReceived(HttpResponse httpResponse) throws IOException, HttpException {
      delegate.responseReceived(httpResponse);
    }

    @Override
    public void consumeContent(ContentDecoder contentDecoder, IOControl ioControl)
        throws IOException {
      delegate.consumeContent(new WrappedContentDecoder(parentContext, contentDecoder), ioControl);
    }

    @Override
    public void responseCompleted(HttpContext httpContext) {
      delegate.responseCompleted(httpContext);
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
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public boolean cancel() {
      return delegate.cancel();
    }
  }

  public static class WrappedRequestProducer implements HttpAsyncRequestProducer {
    private final Context parentContext;
    private final HttpOtelContext httpOtelContext;
    private final HttpAsyncRequestProducer delegate;
    private final WrappedFutureCallback<?> wrappedFutureCallback;

    public WrappedRequestProducer(
        Context parentContext,
        HttpOtelContext httpOtelContext,
        HttpAsyncRequestProducer delegate,
        WrappedFutureCallback<?> wrappedFutureCallback) {
      this.parentContext = parentContext;
      this.httpOtelContext = httpOtelContext;
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

      ApacheHttpClientRequest otelRequest;
      otelRequest = new ApacheHttpClientRequest(parentContext, target, request);

      if (instrumenter().shouldStart(parentContext, otelRequest)) {
        Context context = instrumenter().start(parentContext, otelRequest);
        wrappedFutureCallback.context = context;
        wrappedFutureCallback.otelRequest = otelRequest;

        // As the http processor instrumentation is going to be called asynchronously,
        // we will need to store the otel context variables in http context for the
        // http processor instrumentation to use
        httpOtelContext.setContext(context);
      }

      return request;
    }

    @Override
    public void produceContent(ContentEncoder encoder, IOControl ioctrl) throws IOException {
      delegate.produceContent(new WrappedContentEncoder(parentContext, encoder), ioctrl);
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

  public static class WrappedContentEncoder implements ContentEncoder {
    private final Context parentContext;
    private final ContentEncoder delegate;

    public WrappedContentEncoder(Context parentContext, ContentEncoder delegate) {
      this.parentContext = parentContext;
      this.delegate = delegate;
    }

    @Override
    public int write(ByteBuffer byteBuffer) throws IOException {
      BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
      metrics.addRequestBytes(byteBuffer.limit());
      return delegate.write(byteBuffer);
    }

    @Override
    public void complete() throws IOException {
      delegate.complete();
    }

    @Override
    public boolean isCompleted() {
      return delegate.isCompleted();
    }
  }

  public static class WrappedContentDecoder implements ContentDecoder {
    private final Context parentContext;
    private final ContentDecoder delegate;

    public WrappedContentDecoder(Context parentContext, ContentDecoder delegate) {
      this.delegate = delegate;
      this.parentContext = parentContext;
    }

    @Override
    public int read(ByteBuffer byteBuffer) throws IOException {
      if (byteBuffer.hasRemaining()) {
        BytesTransferMetrics metrics = createOrGetWithParentContext(parentContext);
        metrics.addResponseBytes(byteBuffer.limit());
      }
      return delegate.read(byteBuffer);
    }

    @Override
    public boolean isCompleted() {
      return delegate.isCompleted();
    }
  }

  public static class WrappedFutureCallback<T> implements FutureCallback<T> {

    private static final Logger logger = Logger.getLogger(WrappedFutureCallback.class.getName());

    private final Context parentContext;
    private final HttpOtelContext httpOtelContext;
    private final FutureCallback<T> delegate;

    private volatile Context context;
    private volatile ApacheHttpClientRequest otelRequest;

    public WrappedFutureCallback(
        Context parentContext, HttpOtelContext httpOtelContext, FutureCallback<T> delegate) {
      this.parentContext = parentContext;
      this.httpOtelContext = httpOtelContext;
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

      instrumenter().end(context, getFinalRequest(), getFinalResponse(result), null);

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
      instrumenter().end(context, getFinalRequest(), getFinalResponse(), ex);

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
      instrumenter().end(context, getFinalRequest(), getFinalResponse(), null);

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
      httpOtelContext.clear();
    }

    private HttpResponse getFinalResponse() {
      return getFinalResponse(null);
    }

    private HttpResponse getFinalResponse(T result) {
      return ApacheHttpClientInternalEntityStorage.getFinalResponse(result, context);
    }

    private ApacheHttpClientRequest getFinalRequest() {
      return ApacheHttpClientInternalEntityStorage.getFinalRequest(otelRequest, context);
    }
  }
}
