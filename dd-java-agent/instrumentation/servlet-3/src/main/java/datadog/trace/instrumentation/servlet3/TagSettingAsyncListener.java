package datadog.trace.instrumentation.servlet3;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.util.Collections;
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
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
        Tags.HTTP_STATUS.set(span, ((HttpServletResponse) event.getSuppliedResponse()).getStatus());
      }
    }
  }

  @Override
  public void onTimeout(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.setTag("timeout", event.getAsyncContext().getTimeout());
      }
    }
  }

  @Override
  public void onError(final AsyncEvent event) throws IOException {
    if (event.getThrowable() != null && activated.compareAndSet(false, true)) {
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
        if (((HttpServletResponse) event.getSuppliedResponse()).getStatus()
            == HttpServletResponse.SC_OK) {
          // exception is thrown in filter chain, but status code is incorrect
          Tags.HTTP_STATUS.set(span, 500);
        }
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, event.getThrowable()));
      }
    }
  }

  /** Finish current span on dispatch. New listener will be attached by Servlet3Advice */
  @Override
  public void onStartAsync(final AsyncEvent event) throws IOException {
    onComplete(event);
  }
}
