/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.ServerBuilder;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.opentelemetry.instrumentation.grpc.v1_6.AbstractGrpcTest;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class GrpcLibraryInstrumentationTest extends AbstractGrpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final AtomicBoolean otherTracerClosed = new AtomicBoolean();

  @Override
  protected ServerBuilder<?> configureServer(ServerBuilder<?> server) {
    server.addStreamTracerFactory(
        new ServerStreamTracer.Factory() {
          @Override
          public ServerStreamTracer newServerStreamTracer(String fullMethodName, Metadata headers) {
            return new ServerStreamTracer() {
              @Override
              public void streamClosed(Status status) {
                otherTracerClosed.set(true);
              }
            };
          }
        });
    GrpcTelemetry.builder(testing.getOpenTelemetry())
        .setServerSpanNameExtractorCustomizer(unused -> request -> "library-server-span")
        .build()
        .configureServerBuilder(server);
    return server;
  }

  @AfterEach
  void verifyOtherTracerClosed() {
    await().untilAsserted(() -> assertThat(otherTracerClosed).isTrue());
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
