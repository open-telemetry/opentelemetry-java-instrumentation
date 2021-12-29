/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.ratpack.OpenTelemetryExecInitializer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ratpack.exec.Execution;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

final class OpenTelemetryHttpClient {

  private final Instrumenter<RequestSpec, HttpResponse> instrumenter;

  OpenTelemetryHttpClient(Instrumenter<RequestSpec, HttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public HttpClient instrument(HttpClient httpClient) throws Exception {
    return httpClient.copyWith(
        httpClientSpec -> {
          httpClientSpec.requestIntercept(
              requestSpec -> {
                Context parentOtelCtx = Context.current();
                if (!instrumenter.shouldStart(parentOtelCtx, requestSpec)) {
                  return;
                }

                Context otelCtx = instrumenter.start(parentOtelCtx, requestSpec);
                Span span = Span.fromContext(otelCtx);
                String path = requestSpec.getUri().getPath();
                span.updateName(path);
                //                span.updateName("HTTP " + requestSpec.getMethod().getName()); //
                // TODO use path instead of [HTTP method]
                span.setAttribute(SemanticAttributes.HTTP_ROUTE, path);
                Execution.current()
                    .add(new OpenTelemetryExecInitializer.ContextHolder(otelCtx, requestSpec));
              });

          httpClientSpec.responseIntercept(
              httpResponse -> {
                OpenTelemetryExecInitializer.ContextHolder contextHolder =
                    Execution.current().get(OpenTelemetryExecInitializer.ContextHolder.class);
                instrumenter.end(
                    contextHolder.context(), contextHolder.requestSpec(), httpResponse, null);
              });

          httpClientSpec.errorIntercept(
              ex -> {
                OpenTelemetryExecInitializer.ContextHolder contextHolder =
                    Execution.current().get(OpenTelemetryExecInitializer.ContextHolder.class);
                instrumenter.end(contextHolder.context(), contextHolder.requestSpec(), null, ex);
              });
        });
  }
}
