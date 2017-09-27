package com.datadoghq.agent.integration;

import io.opentracing.ActiveSpan;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jboss.byteman.rule.Rule;

@Slf4j
public class Servlet3Helper extends Servlet2Helper {

  public Servlet3Helper(final Rule rule) {
    super(rule);
  }

  /**
   * The distinction with this method compared with Servlet2Helper.onResponse is the addition of the
   * async support.
   *
   * @param req
   * @param resp
   */
  @Override
  public void onResponse(final HttpServletRequest req, final HttpServletResponse resp) {
    if (req.getAttribute(SERVER_SPAN_CONTEXT) == null) {
      // Doesn't look like an active span was started at the beginning
      return;
    }

    final ActiveSpan span = tracer.activeSpan();
    if (span != null) {
      if (req.isAsyncStarted()) {
        addAsyncListeners(req, resp, span);
      } else {
        ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(req, resp, span);
      }
      span.deactivate();
    }
  }

  private void addAsyncListeners(
      final HttpServletRequest req, final HttpServletResponse resp, final ActiveSpan span) {

    final ActiveSpan.Continuation cont = span.capture();
    final AtomicBoolean activated = new AtomicBoolean(false);
    // what if async is already finished? This would not be called
    req.getAsyncContext()
        .addListener(
            new AsyncListener() {
              @Override
              public void onComplete(final AsyncEvent event) throws IOException {
                if (activated.compareAndSet(false, true)) {
                  try (ActiveSpan activeSpan = cont.activate()) {
                    ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(
                        (HttpServletRequest) event.getSuppliedRequest(),
                        (HttpServletResponse) event.getSuppliedResponse(),
                        span);
                  }
                }
              }

              @Override
              public void onTimeout(final AsyncEvent event) throws IOException {
                if (activated.compareAndSet(false, true)) {
                  try (ActiveSpan activeSpan = cont.activate()) {
                    ServletFilterSpanDecorator.STANDARD_TAGS.onTimeout(
                        (HttpServletRequest) event.getSuppliedRequest(),
                        (HttpServletResponse) event.getSuppliedResponse(),
                        event.getAsyncContext().getTimeout(),
                        span);
                  }
                }
              }

              @Override
              public void onError(final AsyncEvent event) throws IOException {
                if (activated.compareAndSet(false, true)) {
                  try (ActiveSpan activeSpan = cont.activate()) {
                    ServletFilterSpanDecorator.STANDARD_TAGS.onError(
                        (HttpServletRequest) event.getSuppliedRequest(),
                        (HttpServletResponse) event.getSuppliedResponse(),
                        event.getThrowable(),
                        span);
                  }
                }
              }

              @Override
              public void onStartAsync(final AsyncEvent event) throws IOException {}
            });
  }
}
