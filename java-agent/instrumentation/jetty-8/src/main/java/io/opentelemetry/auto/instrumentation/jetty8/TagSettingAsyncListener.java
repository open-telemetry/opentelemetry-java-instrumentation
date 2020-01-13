package io.opentelemetry.auto.instrumentation.jetty8;

import static io.opentelemetry.auto.instrumentation.jetty8.JettyDecorator.DECORATE;

import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

public class TagSettingAsyncListener implements AsyncListener {
  private final AtomicBoolean activated;
  private final AgentSpan span;

  public TagSettingAsyncListener(final AtomicBoolean activated, final AgentSpan span) {
    this.activated = activated;
    this.span = span;
  }

  @Override
  public void onComplete(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      DECORATE.onResponse(span, (HttpServletResponse) event.getSuppliedResponse());
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onTimeout(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      span.setError(true);
      span.setAttribute("timeout", event.getAsyncContext().getTimeout());
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onError(final AsyncEvent event) throws IOException {
    if (event.getThrowable() != null && activated.compareAndSet(false, true)) {
      DECORATE.onResponse(span, (HttpServletResponse) event.getSuppliedResponse());
      if (((HttpServletResponse) event.getSuppliedResponse()).getStatus()
          == HttpServletResponse.SC_OK) {
        // exception is thrown in filter chain, but status code is incorrect
        span.setAttribute(Tags.HTTP_STATUS, 500);
        span.setError(true);
      }
      DECORATE.onError(span, event.getThrowable());
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onStartAsync(final AsyncEvent event) throws IOException {}
}
