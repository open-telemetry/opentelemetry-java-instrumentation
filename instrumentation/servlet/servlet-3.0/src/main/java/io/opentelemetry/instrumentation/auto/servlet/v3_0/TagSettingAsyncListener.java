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

package io.opentelemetry.instrumentation.auto.servlet.v3_0;

import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

public class TagSettingAsyncListener implements AsyncListener {
  private static final Servlet3HttpServerTracer servletHttpServerTracer =
      new Servlet3HttpServerTracer();

  private final AtomicBoolean responseHandled;
  private final Span span;

  public TagSettingAsyncListener(final AtomicBoolean responseHandled, final Span span) {
    this.responseHandled = responseHandled;
    this.span = span;
  }

  @Override
  public void onComplete(final AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      servletHttpServerTracer.end(span, (HttpServletResponse) event.getSuppliedResponse());
    }
  }

  @Override
  public void onTimeout(final AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      servletHttpServerTracer.onTimeout(span, event.getAsyncContext().getTimeout());
    }
  }

  @Override
  public void onError(final AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      servletHttpServerTracer.endExceptionally(
          span, event.getThrowable(), (HttpServletResponse) event.getSuppliedResponse());
    }
  }

  /** Transfer the listener over to the new context. */
  @Override
  public void onStartAsync(final AsyncEvent event) {
    event.getAsyncContext().addListener(this);
  }
}
