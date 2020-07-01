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
package io.opentelemetry.spring.instrumentation.trace.httpclients;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

/** Intercepts outbound Apache HTTP requests and adds the current span context */
public final class ApacheHttpClientInterceptor implements HttpRequestInterceptor {

  private static final HttpTextFormat.Setter<HttpRequest> SETTER =
      new HttpTextFormat.Setter<HttpRequest>() {
        @Override
        public void set(HttpRequest carrier, String key, String value) {
          carrier.addHeader(key, value);
        }
      };

  private final Tracer tracer;

  public ApacheHttpClientInterceptor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
    Span currentSpan = tracer.getCurrentSpan();

    try (Scope scope = tracer.withSpan(currentSpan)) {
      currentSpan.addEvent("ApacheHttpClient request sent");
      OpenTelemetry.getPropagators().getHttpTextFormat().inject(Context.current(), request, SETTER);
    }
  }
}
