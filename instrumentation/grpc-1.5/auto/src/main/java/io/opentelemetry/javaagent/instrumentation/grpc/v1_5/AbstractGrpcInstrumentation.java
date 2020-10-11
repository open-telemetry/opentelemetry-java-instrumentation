/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_5;

import io.opentelemetry.javaagent.tooling.Instrumenter;

abstract class AbstractGrpcInstrumentation extends Instrumenter.Default {

  public AbstractGrpcInstrumentation() {
    super("grpc");
  }

  @Override
  public final String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.grpc.v1_5.common.GrpcHelper",
      "io.opentelemetry.instrumentation.grpc.v1_5.client.GrpcClientTracer",
      "io.opentelemetry.instrumentation.grpc.v1_5.client.GrpcInjectAdapter",
      "io.opentelemetry.instrumentation.grpc.v1_5.client.TracingClientInterceptor",
      "io.opentelemetry.instrumentation.grpc.v1_5.client.TracingClientInterceptor$TracingClientCall",
      "io.opentelemetry.instrumentation.grpc.v1_5.client.TracingClientInterceptor$TracingClientCallListener",
      "io.opentelemetry.instrumentation.grpc.v1_5.server.GrpcExtractAdapter",
      "io.opentelemetry.instrumentation.grpc.v1_5.server.GrpcServerTracer",
      "io.opentelemetry.instrumentation.grpc.v1_5.server.TracingServerInterceptor",
      "io.opentelemetry.instrumentation.grpc.v1_5.server.TracingServerInterceptor$TracingServerCall",
      "io.opentelemetry.instrumentation.grpc.v1_5.server.TracingServerInterceptor$TracingServerCallListener",
    };
  }
}
