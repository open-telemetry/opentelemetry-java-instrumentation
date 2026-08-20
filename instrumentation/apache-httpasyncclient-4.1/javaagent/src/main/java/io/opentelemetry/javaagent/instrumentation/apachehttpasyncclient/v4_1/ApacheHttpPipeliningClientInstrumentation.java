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
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;

class ApacheHttpPipeliningClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    // added in 4.1
    return hasClassesNamed("org.apache.http.nio.client.HttpPipeliningClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.http.nio.client.HttpPipeliningClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute")
            .and(takesArguments(5))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("java.util.List")))
            .and(takesArgument(2, named("java.util.List")))
            .and(takesArgument(3, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(4, named("org.apache.http.concurrent.FutureCallback"))),
        getClass().getName() + "$PipeliningClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class PipeliningClientAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 1, index = 0),
      @ToArgument(value = 2, index = 1),
      @ToArgument(value = 4, index = 2)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) @Nullable HttpHost target,
        @Advice.Argument(1) @Nullable List<? extends HttpAsyncRequestProducer> requestProducers,
        @Advice.Argument(2) @Nullable
            List<? extends HttpAsyncResponseConsumer<?>> responseConsumers,
        @Advice.Argument(4) @Nullable FutureCallback<?> futureCallback) {
      Context parentContext = currentContext();

      if (requestProducers == null
          || responseConsumers == null
          || requestProducers.size() != responseConsumers.size()) {
        return new Object[] {requestProducers, responseConsumers, futureCallback};
      }

      int size = requestProducers.size();
      List<HttpAsyncRequestProducer> modifiedRequestProducers = new ArrayList<>(size);
      List<HttpAsyncResponseConsumer<?>> modifiedResponseConsumers = new ArrayList<>(size);
      List<PipeliningRequestState> requestStates = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
        HttpAsyncRequestProducer requestProducer = requestProducers.get(i);
        HttpAsyncResponseConsumer<?> responseConsumer = responseConsumers.get(i);
        if (requestProducer == null || responseConsumer == null) {
          return new Object[] {requestProducers, responseConsumers, futureCallback};
        }

        PipeliningRequestState state = new PipeliningRequestState(parentContext, target);
        requestStates.add(state);
        modifiedRequestProducers.add(new DelegatingRequestProducer(requestProducer, state));
        modifiedResponseConsumers.add(new DelegatingResponseConsumer<>(responseConsumer, state));
      }

      return new Object[] {
        modifiedRequestProducers,
        modifiedResponseConsumers,
        new ContextPropagatingFutureCallback<>(parentContext, futureCallback, requestStates)
      };
    }
  }

  public static class PipeliningRequestState {
    private final Context parentContext;
    @Nullable private final HttpHost target;

    private boolean started;
    private boolean ended;

    @Nullable private Context context;
    @Nullable private ApacheHttpClientRequest request;
    @Nullable private HttpResponse response;

    public PipeliningRequestState(Context parentContext, @Nullable HttpHost target) {
      this.parentContext = parentContext;
      this.target = target;
    }

    public synchronized void start(HttpRequest httpRequest) {
      if (started || ended) {
        return;
      }
      started = true;

      ApacheHttpClientRequest request = new ApacheHttpClientRequest(target, httpRequest);
      if (instrumenter().shouldStart(parentContext, request)) {
        this.request = request;
        context = instrumenter().start(parentContext, request);
      }
    }

    public synchronized void setResponse(HttpResponse response) {
      if (!ended) {
        this.response = response;
      }
    }

    public synchronized boolean hasResponse() {
      return response != null;
    }

    public void end(@Nullable Throwable error) {
      Context context;
      ApacheHttpClientRequest request;
      HttpResponse response;

      synchronized (this) {
        if (ended) {
          return;
        }
        ended = true;
        context = this.context;
        request = this.request;
        response = this.response;
      }

      if (context != null && request != null) {
        instrumenter().end(context, request, response, error);
      }
    }
  }

  public static class DelegatingRequestProducer implements HttpAsyncRequestProducer {
    private final HttpAsyncRequestProducer delegate;
    private final PipeliningRequestState state;

    public DelegatingRequestProducer(
        HttpAsyncRequestProducer delegate, PipeliningRequestState state) {
      this.delegate = delegate;
      this.state = state;
    }

    @Override
    public HttpHost getTarget() {
      return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
      HttpRequest request = delegate.generateRequest();
      if (request != null) {
        state.start(request);
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

  public static class DelegatingResponseConsumer<T> implements HttpAsyncResponseConsumer<T> {
    private final HttpAsyncResponseConsumer<T> delegate;
    private final PipeliningRequestState state;

    @Nullable private Exception exception;

    public DelegatingResponseConsumer(
        HttpAsyncResponseConsumer<T> delegate, PipeliningRequestState state) {
      this.delegate = delegate;
      this.state = state;
    }

    @Override
    public void responseReceived(HttpResponse response) throws IOException, HttpException {
      state.setResponse(response);
      delegate.responseReceived(response);
    }

    @Override
    public void consumeContent(ContentDecoder decoder, IOControl ioctrl) throws IOException {
      delegate.consumeContent(decoder, ioctrl);
    }

    @Override
    public void responseCompleted(HttpContext context) {
      delegate.responseCompleted(context);
    }

    @Override
    public void failed(Exception ex) {
      try {
        delegate.failed(ex);
      } finally {
        state.end(ex);
      }
    }

    @Override
    @Nullable
    public Exception getException() {
      exception = delegate.getException();
      return exception;
    }

    @Override
    @Nullable
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
      try {
        delegate.close();
      } catch (RuntimeException e) {
        state.end(e);
        throw e;
      } finally {
        if (exception != null || state.hasResponse()) {
          state.end(exception);
        }
      }
    }
  }

  public static class ContextPropagatingFutureCallback<T> implements FutureCallback<T> {
    private final Context parentContext;
    @Nullable private final FutureCallback<T> delegate;
    private final List<PipeliningRequestState> requestStates;

    public ContextPropagatingFutureCallback(
        Context parentContext,
        @Nullable FutureCallback<T> delegate,
        List<PipeliningRequestState> requestStates) {
      this.parentContext = parentContext;
      this.delegate = delegate;
      this.requestStates = requestStates;
    }

    @Override
    public void completed(T result) {
      endRequestStates(null);
      if (delegate == null) {
        return;
      }
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.completed(result);
      }
    }

    @Override
    public void failed(Exception ex) {
      endRequestStates(ex);
      if (delegate == null) {
        return;
      }
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.failed(ex);
      }
    }

    @Override
    public void cancelled() {
      endRequestStates(null);
      if (delegate == null) {
        return;
      }
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.cancelled();
      }
    }

    private void endRequestStates(@Nullable Throwable error) {
      try {
        for (PipeliningRequestState state : requestStates) {
          state.end(error);
        }
      } finally {
        requestStates.clear();
      }
    }
  }
}
