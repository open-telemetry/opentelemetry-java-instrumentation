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

package io.opentelemetry.instrumentation.auto.httpclient;

import static io.opentelemetry.instrumentation.auto.httpclient.JdkHttpClientTracer.TRACER;

import io.opentelemetry.trace.Span;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ResponseConsumer implements BiConsumer<HttpResponse<?>, Throwable> {
  private final Span span;

  public ResponseConsumer(Span span) {
    this.span = span;
  }

  @Override
  public void accept(HttpResponse<?> httpResponse, Throwable throwable) {
    if (throwable == null) {
      TRACER.end(span, httpResponse);
    } else {
      final Throwable cause = throwable.getCause();
      TRACER.endExceptionally(span, httpResponse, Objects.requireNonNullElse(cause, throwable));
    }
  }
}
