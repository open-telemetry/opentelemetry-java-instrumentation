/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class GrpcTest extends AbstractGrpcTest implements LibraryTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder server) {
    return server.intercept(GrpcTracing.create(getOpenTelemetry()).newServerInterceptor())
  }

  @Override
  ManagedChannelBuilder configureClient(ManagedChannelBuilder client) {
    return client.intercept(GrpcTracing.create(getOpenTelemetry()).newClientInterceptor())
  }
}
