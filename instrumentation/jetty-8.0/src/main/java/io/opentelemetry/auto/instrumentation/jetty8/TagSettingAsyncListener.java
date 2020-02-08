package io.opentelemetry.auto.instrumentation.jetty8;

import static io.opentelemetry.auto.instrumentation.jetty8.JettyDecorator.DECORATE;

import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

public class TagSettingAsyncListener implements AsyncListener {
  private final AtomicBoolean activated;
  private final Span span;

  public TagSettingAsyncListener(final AtomicBoolean activated, final Span span) {
    this.activated = activated;
    this.span = span;
  }

  @Override
  public void onComplete(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      DECORATE.onResponse(span, (HttpServletResponse) event.getSuppliedResponse());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onTimeout(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      span.setStatus(Status.UNKNOWN);
      span.setAttribute("timeout", event.getAsyncContext().getTimeout());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onError(final AsyncEvent event) throws IOException {
    final Throwable throwable = event.getThrowable();
    if (throwable != null && activated.compareAndSet(false, true)) {
      DECORATE.onResponse(span, (HttpServletResponse) event.getSuppliedResponse());
      if (((HttpServletResponse) event.getSuppliedResponse()).getStatus()
          == HttpServletResponse.SC_OK) {
        // exception is thrown in filter chain, but status code is incorrect
        span.setAttribute(Tags.HTTP_STATUS, 500);
      }
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onStartAsync(final AsyncEvent event) throws IOException {}
}
