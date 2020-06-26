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

package io.opentelemetry.auto.instrumentation.jetty;

import static io.opentelemetry.auto.instrumentation.jetty.JettyDecorator.DECORATE;

import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
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
  public void onComplete(final AsyncEvent event) {
    if (activated.compareAndSet(false, true)) {
      DECORATE.onResponse(span, (HttpServletResponse) event.getSuppliedResponse());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onTimeout(final AsyncEvent event) {
    if (activated.compareAndSet(false, true)) {
      span.setStatus(Status.UNKNOWN);
      span.setAttribute("timeout", event.getAsyncContext().getTimeout());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onError(final AsyncEvent event) {
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
  public void onStartAsync(final AsyncEvent event) {}
}
