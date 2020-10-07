/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grpc.v1_5

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.opentelemetry.auto.test.AgentTestTrait
import io.opentelemetry.instrumentation.grpc.v1_5.AbstractGrpcTest

class GrpcTest extends AbstractGrpcTest implements AgentTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder server) {
    return server
  }

  @Override
  ManagedChannelBuilder configureClient(ManagedChannelBuilder client) {
    return client
  }
}
