/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/** Entrypoint for tracing Spring {@link org.springframework.web.client.RestTemplate}. */
public final class SpringWebTracing {

  /** Returns a new {@link SpringWebTracing} configured with the given {@link OpenTelemetry}. */
  public static SpringWebTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringWebTracingBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static SpringWebTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebTracingBuilder(openTelemetry);
  }

  private final Instrumenter<HttpRequest, ClientHttpResponse> instrumenter;

  SpringWebTracing(Instrumenter<HttpRequest, ClientHttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /**
   * Returns a new {@link ClientHttpRequestInterceptor} that can be used with {@link
   * RestTemplate#getInterceptors()}. For example:
   *
   * <pre>{@code
   * restTemplate.getInterceptors().add(SpringWebTracing.create(openTelemetry).newInterceptor());
   * }</pre>
   */
  public ClientHttpRequestInterceptor newInterceptor() {
    return new RestTemplateInterceptor(instrumenter);
  }
}
