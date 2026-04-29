/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import static org.assertj.core.api.Assertions.assertThat;

import custom.CustomService;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThriftTest extends AbstractThriftTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static ThriftTelemetry telemetry;

  @BeforeAll
  static void setup() {
    telemetry = ThriftTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected TProcessor configure(TProcessor processor, String serviceName) {
    return telemetry.wrapServerProcessor(processor, serviceName);
  }

  @Override
  protected TProtocol configure(TProtocol protocol, String serviceName) {
    return telemetry.wrapClientProtocol(protocol, serviceName);
  }

  @Override
  protected TProtocolFactory configure(
      TProtocolFactory protocolFactory, String serviceName, TTransport transport) {
    return telemetry.wrapClientProtocolFactory(protocolFactory, serviceName, transport);
  }

  @Override
  protected CustomService.AsyncIface configure(CustomService.AsyncClient asyncClient) {
    return telemetry.wrapAsyncClient(asyncClient, CustomService.AsyncIface.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "threadPool"})
  void instrumentedClientPlainSimpleServer(String serverKind) throws Exception {
    int port = startServer(serverKind, false);
    CustomService.Client client = createClient(port, true);

    assertThat(client.say("Plain", "Server")).isEqualTo("Say Plain Server");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, "say", port).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "threadPool"})
  void plainClientInstrumentedSimpleServer(String serverKind) throws Exception {
    int port = startServer(serverKind, true);
    CustomService.Client client = createClient(port, false);

    assertThat(client.say("Instrumented", "Server")).isEqualTo("Say Instrumented Server");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertServerSpan(span, "say", port).hasNoParent()));
  }
}
