/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.ContextHolder;
import io.opentelemetry.semconv.HttpAttributes;
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
                Context parentOtelCtx =
                    Execution.current().maybeGet(Context.class).orElse(Context.current());
                if (!instrumenter.shouldStart(parentOtelCtx, requestSpec)) {
                  return;
                }

                Context otelCtx = instrumenter.start(parentOtelCtx, requestSpec);
                Span span = Span.fromContext(otelCtx);
                String path = requestSpec.getUri().getPath();
                span.setAttribute(HttpAttributes.HTTP_ROUTE, path);
                Execution.current().add(new ContextHolder(otelCtx, requestSpec));
              });

          httpClientSpec.responseIntercept(
              httpResponse -> {
                Execution execution = Execution.current();
                ContextHolder contextHolder = execution.get(ContextHolder.class);
                execution.remove(ContextHolder.class);
                instrumenter.end(
                    contextHolder.context(), contextHolder.requestSpec(), httpResponse, null);
              });

          httpClientSpec.errorIntercept(
              ex -> {
                Execution execution = Execution.current();
                ContextHolder contextHolder = execution.get(ContextHolder.class);
                execution.remove(ContextHolder.class);
                instrumenter.end(contextHolder.context(), contextHolder.requestSpec(), null, ex);
              });
        });
  }
}
