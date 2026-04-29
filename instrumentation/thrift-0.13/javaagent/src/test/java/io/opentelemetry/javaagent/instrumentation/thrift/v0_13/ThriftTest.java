/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import custom.CustomService;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.thrift.v0_13.AbstractThriftTest;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThriftTest extends AbstractThriftTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected TProcessor configure(TProcessor processor, String serviceName) {
    return processor;
  }

  @Override
  protected TProtocol configure(TProtocol protocol, String serviceName) {
    return protocol;
  }

  @Override
  protected TProtocolFactory configure(
      TProtocolFactory protocolFactory, String serviceName, TTransport transport) {
    return protocolFactory;
  }

  @Override
  protected CustomService.AsyncIface configure(CustomService.AsyncClient asyncClient) {
    return asyncClient;
  }
}
