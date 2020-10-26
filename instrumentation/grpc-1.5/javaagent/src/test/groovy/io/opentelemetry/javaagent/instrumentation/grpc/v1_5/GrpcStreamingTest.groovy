/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_5

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.opentelemetry.instrumentation.grpc.v1_5.AbstractGrpcStreamingTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class GrpcStreamingTest extends AbstractGrpcStreamingTest implements AgentTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder server) {
    return server
  }

  @Override
  ManagedChannelBuilder configureClient(ManagedChannelBuilder client) {
    return client
  }
}
