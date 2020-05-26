/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

public class TagSettingAsyncListener implements AsyncListener {
  private final AtomicBoolean responseHandled;
  private final Span span;
  private final Servlet3HttpServerTracer servletHttpServerTracer;

  public TagSettingAsyncListener(final AtomicBoolean responseHandled, final Span span,
      Servlet3HttpServerTracer servletHttpServerTracer) {
    this.responseHandled = responseHandled;
    this.span = span;
    this.servletHttpServerTracer = servletHttpServerTracer;
  }

  @Override
  public void onComplete(final AsyncEvent event) {
    servletHttpServerTracer
        .onResponse(
            (HttpServletResponse) event.getSuppliedResponse(),
            null,
            span,
            responseHandled);
  }

  @Override
  public void onTimeout(final AsyncEvent event) {
    servletHttpServerTracer.onTimeout(responseHandled, span, event.getAsyncContext().getTimeout());
  }

  @Override
  public void onError(final AsyncEvent event) {
    servletHttpServerTracer
        .onResponse(
            (HttpServletResponse) event.getSuppliedResponse(),
            event.getThrowable(),
            span,
            responseHandled);
  }

  /** Transfer the listener over to the new context. */
  @Override
  public void onStartAsync(final AsyncEvent event) {
    event.getAsyncContext().addListener(this);
  }
}
