/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.URI;
import java.net.URISyntaxException;
import ratpack.exec.Execution;
import ratpack.http.client.HttpClient;
import ratpack.service.Service;
import ratpack.service.StartEvent;

public class BarService implements Service {
  private final String url;
  private final InstrumentationExtension testing;

  public BarService(String url, InstrumentationExtension testing) {
    this.url = url;
    this.testing = testing;
  }

  protected void generateSpan(StartEvent event) {
    Context parentContext = Context.current();
    testing.runWithSpan(
        "a-span",
        () -> {
          Span span = Span.current();
          Context otelContext = parentContext.with(span);
          try (Scope scope = otelContext.makeCurrent()) {
            Execution.current().add(Context.class, otelContext);
            HttpClient httpClient = event.getRegistry().get(HttpClient.class);
            httpClient
                .get(new URI(url))
                .flatMap(response -> httpClient.get(new URI(url)))
                .then(response -> span.end());
          } catch (URISyntaxException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Override
  public void onStart(StartEvent event) {
    generateSpan(event);
  }
}
