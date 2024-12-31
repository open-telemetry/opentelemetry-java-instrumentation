/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.client;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import ratpack.exec.Execution;
import ratpack.http.client.HttpClient;
import ratpack.service.Service;
import ratpack.service.StartEvent;

public class BarService implements Service {
  private final String url;
  private final CountDownLatch latch;
  private final Tracer tracer;

  public BarService(CountDownLatch latch, String url, OpenTelemetry openTelemetry) {
    this.latch = latch;
    this.url = url;
    this.tracer = openTelemetry.getTracerProvider().tracerBuilder("testing").build();
  }

  @Override
  public void onStart(StartEvent event) {
    Context parentContext = Context.current();
    Span span = tracer.spanBuilder("a-span").setParent(parentContext).startSpan();

    Context otelContext = parentContext.with(span);
    try (Scope scope = otelContext.makeCurrent()) {
      Execution.current().add(Context.class, otelContext);
      HttpClient httpClient = event.getRegistry().get(HttpClient.class);
      httpClient
          .get(new URI(url))
          .flatMap(response -> httpClient.get(new URI(url)))
          .then(
              response -> {
                span.end();
                latch.countDown();
              });
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
