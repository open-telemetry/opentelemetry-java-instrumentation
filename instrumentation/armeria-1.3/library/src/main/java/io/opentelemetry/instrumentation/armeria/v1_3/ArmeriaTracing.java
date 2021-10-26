/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Function;

/** Entrypoint for tracing Armeria services or clients. */
public final class ArmeriaTracing {

  /** Returns a new {@link ArmeriaTracing} configured with the given {@link OpenTelemetry}. */
  public static ArmeriaTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static ArmeriaTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new ArmeriaTracingBuilder(openTelemetry);
  }

  private final Instrumenter<ClientRequestContext, RequestLog> clientInstrumenter;
  private final Instrumenter<ServiceRequestContext, RequestLog> serverInstrumenter;

  ArmeriaTracing(
      Instrumenter<ClientRequestContext, RequestLog> clientInstrumenter,
      Instrumenter<ServiceRequestContext, RequestLog> serverInstrumenter) {
    this.clientInstrumenter = clientInstrumenter;
    this.serverInstrumenter = serverInstrumenter;
  }

  /**
   * Returns a new {@link HttpClient} decorator for use with methods like {@link
   * com.linecorp.armeria.client.ClientBuilder#decorator(Function)}.
   */
  public Function<? super HttpClient, ? extends HttpClient> newClientDecorator() {
    return client -> new OpenTelemetryClient(client, clientInstrumenter);
  }

  /**
   * Returns a new {@link HttpService} decorator for use with methods like {@link
   * HttpService#decorate(Function)}.
   */
  public Function<? super HttpService, ? extends HttpService> newServiceDecorator() {
    return service -> new OpenTelemetryService(service, serverInstrumenter);
  }
}
