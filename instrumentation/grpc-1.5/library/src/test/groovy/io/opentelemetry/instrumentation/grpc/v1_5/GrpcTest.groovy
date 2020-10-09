/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.opentelemetry.instrumentation.test.InstrumentationTestTrait
import io.opentelemetry.instrumentation.grpc.v1_5.client.TracingClientInterceptor
import io.opentelemetry.instrumentation.grpc.v1_5.server.TracingServerInterceptor

class GrpcTest extends AbstractGrpcTest implements InstrumentationTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder server) {
    return server.intercept(TracingServerInterceptor.newInterceptor())
  }

  @Override
  ManagedChannelBuilder configureClient(ManagedChannelBuilder client) {
    return client.intercept(TracingClientInterceptor.newInterceptor())
  }
}
