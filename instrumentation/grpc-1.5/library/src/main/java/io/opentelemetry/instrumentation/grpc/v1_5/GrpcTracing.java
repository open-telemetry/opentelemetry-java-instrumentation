/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.OpenTelemetry;

/** Entrypoint for tracing gRPC servers or clients. */
public final class GrpcTracing {

  /** Returns a new {@link GrpcTracing} configured with the given {@link OpenTelemetry}. */
  public static GrpcTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /** Returns a new {@link GrpcTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static GrpcTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new GrpcTracingBuilder(openTelemetry);
  }

  private final boolean captureExperimentalSpanAttributes;

  private final GrpcClientTracer clientTracer;
  private final GrpcServerTracer serverTracer;

  GrpcTracing(OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    clientTracer = new GrpcClientTracer(openTelemetry);
    serverTracer = new GrpcServerTracer(openTelemetry);
  }

  /**
   * Returns a new {@link TracingClientInterceptor} for use with methods like {@link
   * io.grpc.ManagedChannelBuilder#intercept(ClientInterceptor...)}.
   */
  public TracingClientInterceptor newClientInterceptor() {
    return new TracingClientInterceptor(clientTracer);
  }

  /**
   * Returns a new {@link TracingServerInterceptor} for use with methods like {@link
   * io.grpc.ServerBuilder#intercept(ServerInterceptor)}.
   */
  public TracingServerInterceptor newServerInterceptor() {
    return new TracingServerInterceptor(serverTracer, captureExperimentalSpanAttributes);
  }
}
