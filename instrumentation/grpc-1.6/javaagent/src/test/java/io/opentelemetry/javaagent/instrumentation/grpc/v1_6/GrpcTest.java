/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.opentelemetry.instrumentation.grpc.v1_6.AbstractGrpcTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class GrpcTest extends AbstractGrpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected ServerBuilder<?> configureServer(ServerBuilder<?> server) {
    return server;
  }

  @Override
  protected ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client) {
    return client;
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
