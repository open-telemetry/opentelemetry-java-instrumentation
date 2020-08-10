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

package io.opentelemetry.instrumentation.spring.httpclients;

import static io.opentelemetry.instrumentation.spring.httpclients.RestTemplateTracer.TRACER;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Wraps RestTemplate requests in a span. Adds the current span context to request headers. */
public final class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private final Tracer tracer;

  public RestTemplateInterceptor(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    Span span = TRACER.startSpan(request);
    try (Scope scope = TRACER.startScope(span, request)) {
      ClientHttpResponse response = execution.execute(request, body);
      TRACER.end(span, response);
      return response;
    }
  }
}
