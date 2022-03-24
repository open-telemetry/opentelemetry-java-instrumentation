/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class GrpcTest extends AbstractGrpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected ServerBuilder<?> configureServer(ServerBuilder<?> server) {
    return server.intercept(
        GrpcTelemetry.create(testing.getOpenTelemetry()).newServerInterceptor());
  }

  @Override
  protected ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client) {
    return client.intercept(
        GrpcTelemetry.create(testing.getOpenTelemetry()).newClientInterceptor());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
