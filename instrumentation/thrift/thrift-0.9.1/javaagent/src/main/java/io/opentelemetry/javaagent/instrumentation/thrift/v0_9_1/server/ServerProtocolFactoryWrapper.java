/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

@SuppressWarnings({"serial"})
public final class ServerProtocolFactoryWrapper implements TProtocolFactory {
  public TProtocolFactory delegate;

  @Override
  public TProtocol getProtocol(TTransport transport) {
    TProtocol protocol = delegate.getProtocol(transport);
    if (protocol instanceof ServerInProtocolWrapper) {
      return protocol;
    }
    return new ServerInProtocolWrapper(protocol);
  }

  public ServerProtocolFactoryWrapper(TProtocolFactory protocolFactory) {
    this.delegate = protocolFactory;
  }
}
