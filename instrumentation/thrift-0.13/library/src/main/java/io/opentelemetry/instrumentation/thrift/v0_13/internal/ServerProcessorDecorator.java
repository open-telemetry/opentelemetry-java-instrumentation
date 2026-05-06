/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerProcessorDecorator implements TProcessor {

  private final TProcessor processor;
  private final String serviceName;
  private final Instrumenter<ThriftRequest, ThriftResponse> instrumenter;

  public ServerProcessorDecorator(
      TProcessor processor,
      String serviceName,
      Instrumenter<ThriftRequest, ThriftResponse> instrumenter) {
    this.processor = processor;
    this.serviceName = serviceName;
    this.instrumenter = instrumenter;
  }

  @Override
  public void process(TProtocol inProtocol, TProtocol outProtocol) throws TException {
    Throwable error = null;
    ServerInProtocolDecorator serverInProtocolDecorator =
        new ServerInProtocolDecorator(inProtocol, serviceName, instrumenter);
    ServerOutProtocolDecorator serverOutProtocolDecorator =
        new ServerOutProtocolDecorator(outProtocol);
    try {
      processor.process(serverInProtocolDecorator, serverOutProtocolDecorator);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      serverInProtocolDecorator.endSpan(error, serverOutProtocolDecorator.hasException());
    }
  }
}
