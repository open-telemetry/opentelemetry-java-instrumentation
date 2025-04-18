/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_3.common;

import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

@SuppressWarnings("all")
public class TMultiplexedProtocolFactory implements TProtocolFactory {
  private final TProtocolFactory protocolFactory;
  private final String serviceName;

  public TMultiplexedProtocolFactory(TProtocolFactory protocolFactory, String serviceName) {
    this.protocolFactory = protocolFactory;
    this.serviceName = serviceName;
  }

  @Override
  public TProtocol getProtocol(TTransport trans) {
    TProtocol baseProtocol = protocolFactory.getProtocol(trans);
    return new TMultiplexedProtocol(baseProtocol, serviceName);
  }
}
