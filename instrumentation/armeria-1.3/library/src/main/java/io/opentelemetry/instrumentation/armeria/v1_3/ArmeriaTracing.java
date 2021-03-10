/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import java.util.Map;
import java.util.function.Function;

/** Entrypoint for tracing Armeria services or clients. */
public final class ArmeriaTracing {

  /** Returns a new {@link ArmeriaTracing} configured with the given {@link OpenTelemetry}. */
  public static ArmeriaTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /**
   * Returns a new {@link ArmeriaTracingBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static ArmeriaTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new ArmeriaTracingBuilder(openTelemetry);
  }

  private final NetPeerAttributes netPeerAttributes;
  private final ArmeriaClientTracer clientTracer;
  private final ArmeriaServerTracer serverTracer;

  ArmeriaTracing(OpenTelemetry openTelemetry, Map<String, String> peerServiceMapping) {
    netPeerAttributes = new NetPeerAttributes(peerServiceMapping);
    clientTracer = new ArmeriaClientTracer(openTelemetry, netPeerAttributes);
    serverTracer = new ArmeriaServerTracer(openTelemetry);
  }

  /**
   * Returns a new {@link HttpClient} decorator for use with methods like {@link
   * com.linecorp.armeria.client.ClientBuilder#decorator(Function)}.
   */
  public Function<? super HttpClient, ? extends HttpClient> newClientDecorator() {
    return client -> new OpenTelemetryClient(client, clientTracer, netPeerAttributes);
  }

  /**
   * Returns a new {@link HttpService} decorator for use with methods like {@link
   * HttpService#decorate(Function)}.
   */
  public Function<? super HttpService, ? extends HttpService> newServiceDecorator() {
    return service -> new OpenTelemetryService(service, serverTracer);
  }
}
