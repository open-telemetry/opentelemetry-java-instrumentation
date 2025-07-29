/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

final class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private final Instrumenter<HttpRequest, ClientHttpResponse> instrumenter;

  RestTemplateInterceptor(Instrumenter<HttpRequest, ClientHttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return execution.execute(request, body);
    }

    Context context = instrumenter.start(parentContext, request);

    ClientHttpResponse response;
    try (Scope ignored = context.makeCurrent()) {
      response = execution.execute(request, body);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }
    instrumenter.end(context, request, response, null);
    return response;
  }
}
