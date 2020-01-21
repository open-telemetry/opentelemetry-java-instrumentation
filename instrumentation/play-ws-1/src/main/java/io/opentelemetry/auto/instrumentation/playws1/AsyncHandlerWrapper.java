package io.opentelemetry.auto.instrumentation.playws1;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.playws1.PlayWSClientDecorator.DECORATE;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseHeaders;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.Response;

public class AsyncHandlerWrapper implements AsyncHandler {
  private final AsyncHandler delegate;
  private final AgentSpan span;
  private final AgentSpan parentSpan;

  private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

  public AsyncHandlerWrapper(final AsyncHandler delegate, final AgentSpan span) {
    this.delegate = delegate;
    this.span = span;
    parentSpan = activeSpan();
  }

  @Override
  public State onBodyPartReceived(final HttpResponseBodyPart content) throws Exception {
    builder.accumulate(content);
    return delegate.onBodyPartReceived(content);
  }

  @Override
  public State onStatusReceived(final HttpResponseStatus status) throws Exception {
    builder.reset();
    builder.accumulate(status);
    return delegate.onStatusReceived(status);
  }

  @Override
  public State onHeadersReceived(final HttpResponseHeaders httpHeaders) throws Exception {
    builder.accumulate(httpHeaders);
    return delegate.onHeadersReceived(httpHeaders);
  }

  @Override
  public Object onCompleted() throws Exception {
    final Response response = builder.build();
    if (response != null) {
      DECORATE.onResponse(span, response);
    }
    DECORATE.beforeFinish(span);
    span.finish();

    if (parentSpan != null) {
      try (final AgentScope scope = activateSpan(parentSpan, false)) {
        return delegate.onCompleted();
      }
    } else {
      return delegate.onCompleted();
    }
  }

  @Override
  public void onThrowable(final Throwable throwable) {
    DECORATE.onError(span, throwable);
    DECORATE.beforeFinish(span);
    span.finish();

    if (parentSpan != null) {
      try (final AgentScope scope = activateSpan(parentSpan, false)) {
        delegate.onThrowable(throwable);
      }
    } else {
      delegate.onThrowable(throwable);
    }
  }
}
