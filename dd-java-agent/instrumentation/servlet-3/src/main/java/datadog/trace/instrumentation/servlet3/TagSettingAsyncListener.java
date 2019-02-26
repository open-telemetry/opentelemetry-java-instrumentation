package datadog.trace.instrumentation.servlet3;

import static datadog.trace.instrumentation.servlet3.Servlet3Decorator.DECORATE;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
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
      span.finish();
    }
  }

  @Override
  public void onTimeout(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.setTag("timeout", event.getAsyncContext().getTimeout());
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onError(final AsyncEvent event) throws IOException {
    if (event.getThrowable() != null && activated.compareAndSet(false, true)) {
      if (((HttpServletResponse) event.getSuppliedResponse()).getStatus()
          == HttpServletResponse.SC_OK) {
        // exception is thrown in filter chain, but status code is incorrect
        Tags.HTTP_STATUS.set(span, 500);
      }
      DECORATE.onError(span, event.getThrowable());
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  /** Finish current span on dispatch. New listener will be attached by Servlet3Advice */
  @Override
  public void onStartAsync(final AsyncEvent event) throws IOException {
    onComplete(event);
  }
}
