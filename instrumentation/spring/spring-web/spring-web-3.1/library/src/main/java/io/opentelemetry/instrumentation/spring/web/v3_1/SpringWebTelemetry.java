/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/** Entrypoint for instrumenting Spring {@link org.springframework.web.client.RestTemplate}. */
public final class SpringWebTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpRequest, ClientHttpResponse> instrumenter;

  SpringWebTelemetry(Instrumenter<HttpRequest, ClientHttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /**
   * Returns an interceptor for instrumenting {@link RestTemplate} requests.
   *
   * <pre>{@code
   * restTemplate.getInterceptors().add(SpringWebTelemetry.create(openTelemetry).createInterceptor());
   * }</pre>
   */
  public ClientHttpRequestInterceptor createInterceptor() {
    return new RestTemplateInterceptor(instrumenter);
  }

  /**
   * Returns an interceptor for instrumenting {@link RestTemplate} requests.
   *
   * <pre>{@code
   * restTemplate.getInterceptors().add(SpringWebTelemetry.create(openTelemetry).createInterceptor());
   * }</pre>
   *
   * @deprecated Use {@link #createInterceptor()} instead.
   */
  @Deprecated
  public ClientHttpRequestInterceptor newInterceptor() {
    return createInterceptor();
  }
}
