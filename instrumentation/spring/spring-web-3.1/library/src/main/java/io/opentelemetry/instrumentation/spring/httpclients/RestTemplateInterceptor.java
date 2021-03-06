/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.httpclients;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Wraps RestTemplate requests in a span. Adds the current span context to request headers. */
public final class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private final RestTemplateTracer tracer;

  public RestTemplateInterceptor(OpenTelemetry openTelemetry) {
    this.tracer = new RestTemplateTracer(openTelemetry);
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    Context parentContext = Context.current();
    if (!tracer.shouldStartSpan(parentContext)) {
      return execution.execute(request, body);
    }

    Context context = tracer.startSpan(parentContext, request, request.getHeaders());
    try (Scope ignored = context.makeCurrent()) {
      ClientHttpResponse response = execution.execute(request, body);
      tracer.end(context, response);
      return response;
    }
    // TODO: endExceptionally?
  }
}
