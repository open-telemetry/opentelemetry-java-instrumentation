/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TTransportException;
import org.junit.jupiter.api.Test;

class CallContextTest {

  private static final Instrumenter<ThriftRequest, ThriftResponse> instrumenter =
      Instrumenter.<ThriftRequest, ThriftResponse>builder(
              OpenTelemetry.noop(), "test", request -> "test")
          .setEnabled(false)
          .buildInstrumenter();

  @Test
  void nestedClientCallRestoresOuterAfterFailure() {
    ClientCallContext outer = startClient();
    try {
      assertThatThrownBy(
              () -> {
                ClientCallContext inner = startClient();
                try {
                  assertThat(ClientCallContext.get()).isSameAs(inner);
                  throw new IllegalStateException("client");
                } finally {
                  inner.close();
                }
              })
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("client");
      assertThat(ClientCallContext.get()).isSameAs(outer);
    } finally {
      outer.close();
    }
    assertThat(ClientCallContext.get()).isNull();
  }

  @Test
  void failedClientInitializationPreservesOuterContext() {
    Instrumenter<ThriftRequest, ThriftResponse> failingInstrumenter =
        Instrumenter.<ThriftRequest, ThriftResponse>builder(
                OpenTelemetry.noop(),
                "test",
                request -> {
                  throw new IllegalStateException("start");
                })
            .buildInstrumenter();
    ClientCallContext outer = startClient();
    try {
      assertThatThrownBy(
              () ->
                  ClientCallContext.start(
                      failingInstrumenter, "test", CallContextTest.class, null, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("start");
      assertThat(ClientCallContext.get()).isSameAs(outer);
    } finally {
      outer.close();
    }
    assertThat(ClientCallContext.get()).isNull();
  }

  @Test
  void nestedServerCallRestoresOuterTransportAfterFailure() throws TTransportException {
    TMemoryBuffer outerTransport = new TMemoryBuffer(32);
    TMemoryBuffer innerTransport = new TMemoryBuffer(32);
    ServerCallContext outer = ServerCallContext.start(outerTransport);
    try {
      assertThatThrownBy(
              () -> {
                ServerCallContext inner = ServerCallContext.start(innerTransport);
                try {
                  assertThat(ServerCallContext.getTransport()).isSameAs(innerTransport);
                  throw new IllegalStateException("server");
                } finally {
                  inner.end();
                }
              })
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("server");
      assertThat(ServerCallContext.getTransport()).isSameAs(outerTransport);
    } finally {
      outer.end();
    }
    assertThat(ServerCallContext.getTransport()).isNull();
  }

  private static ClientCallContext startClient() {
    return ClientCallContext.start(instrumenter, "test", CallContextTest.class, null, null);
  }
}
