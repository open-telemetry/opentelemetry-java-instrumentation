/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.api.OpenTelemetry;
import java.util.function.Function;

/** Entrypoint for tracing Armeria services or clients. */
public final class ArmeriaTracing {

  /** Returns a new {@link ArmeriaTracing} configured with the given {@link OpenTelemetry}. */
  public static ArmeriaTracing create(OpenTelemetry openTelemetry) {
    return new ArmeriaTracing(openTelemetry);
  }

  private final ArmeriaClientTracer clientTracer;
  private final ArmeriaServerTracer serverTracer;

  private ArmeriaTracing(OpenTelemetry openTelemetry) {
    clientTracer = new ArmeriaClientTracer(openTelemetry);
    serverTracer = new ArmeriaServerTracer(openTelemetry);
  }

  /**
   * Returns a new {@link HttpClient} decorator for use with methods like {@link
   * com.linecorp.armeria.client.ClientBuilder#decorator(Function)}.
   */
  public Function<? super HttpClient, ? extends HttpClient> newClientDecorator() {
    return client -> new OpenTelemetryClient(client, clientTracer);
  }

  /**
   * Returns a new {@link HttpService} decorator for use with methods like {@link
   * HttpService#decorate(Function)}.
   */
  public Function<? super HttpService, ? extends HttpService> newServiceDecorator() {
    return service -> new OpenTelemetryService(service, serverTracer);
  }
}
