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
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Wraps RestTemplate requests in a span. Adds the current span context to request headers. */
public final class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private static final HttpTextFormat.Setter<HttpRequest> SETTER =
      new HttpTextFormat.Setter<HttpRequest>() {
        @Override
        public void set(HttpRequest carrier, String key, String value) {
          carrier.getHeaders().set(key, value);
        }
      };

  private final Tracer tracer;

  public RestTemplateInterceptor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    String spanName = createSpanName(request);
    Span currentSpan = tracer.spanBuilder(spanName).setSpanKind(Span.Kind.CLIENT).startSpan();

    try (Scope scope = tracer.withSpan(currentSpan)) {
      OpenTelemetry.getPropagators().getHttpTextFormat().inject(Context.current(), request, SETTER);
      ClientHttpResponse response = execution.execute(request, body);
      return response;
    } finally {
      currentSpan.end();
    }
  }

  private String createSpanName(HttpRequest request) {
    return request.getMethodValue() + " " + request.getURI().toString();
  }
}
