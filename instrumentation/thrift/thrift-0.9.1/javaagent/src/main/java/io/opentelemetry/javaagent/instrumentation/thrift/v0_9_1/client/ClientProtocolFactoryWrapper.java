/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

public final class ClientProtocolFactoryWrapper implements TProtocolFactory {
  public TProtocolFactory delegate;
  public TTransport transport;
  public String serviceName;

  @Override
  public TProtocol getProtocol(TTransport transport) {
    TProtocol protocol = this.delegate.getProtocol(transport);
    if (protocol instanceof ClientOutProtocolWrapper) {
      if (transport != null) {
        ((ClientOutProtocolWrapper) protocol).updateTransport(this.transport);
      }
      ((ClientOutProtocolWrapper) protocol).setServiceName(this.serviceName);
      return protocol;
    }
    protocol = new ClientOutProtocolWrapper(protocol, this.serviceName, null);
    if (transport != null) {
      ((ClientOutProtocolWrapper) protocol).updateTransport(this.transport);
    }
    return protocol;
  }

  public ClientProtocolFactoryWrapper(
      TProtocolFactory protocolFactory, TTransport transport, String serviceName) {
    this.delegate = protocolFactory;
    this.transport = transport;
    this.serviceName = serviceName;
  }
}
