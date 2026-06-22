/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import org.apache.thrift.TAsyncProcessor;
import org.apache.thrift.TBaseAsyncProcessor;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.AbstractNonblockingServer;
import org.apache.thrift.server.FrameBufferUtil;
import org.apache.thrift.transport.TTransport;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerAsyncProcessorDecorator implements TAsyncProcessor, TProcessor {
  private final TBaseAsyncProcessor<?> processor;
  private final String serviceName;

  public ServerAsyncProcessorDecorator(TBaseAsyncProcessor<?> processor, String serviceName) {
    this.processor = processor;
    this.serviceName = serviceName;
  }

  @Override
  public void process(AbstractNonblockingServer.AsyncFrameBuffer fb) throws TException {
    if (!(fb.getInputProtocol() instanceof ServerInProtocolDecorator)
        || !(fb.getOutputProtocol() instanceof ServerOutProtocolDecorator)) {
      processor.process(fb);
      return;
    }

    TTransport transport = FrameBufferUtil.getTransport(fb);
    ServerInProtocolDecorator serverInProtocolDecorator =
        (ServerInProtocolDecorator) fb.getInputProtocol();
    ServerOutProtocolDecorator serverOutProtocolDecorator =
        (ServerOutProtocolDecorator) fb.getOutputProtocol();

    serverInProtocolDecorator.setServiceName(serviceName);
    ServerCallContext serverCallContext = ServerCallContext.start(transport);
    Throwable error = null;
    try {
      processor.process(fb);
    } catch (Throwable t) {
      error = t;
    } finally {
      serverCallContext.end();
    }
    if (serverInProtocolDecorator.isOneway()
        || serverOutProtocolDecorator.hasException()
        || error != null) {
      serverInProtocolDecorator.endSpan(error, serverOutProtocolDecorator.hasException());
    }
  }

  @Override
  public void process(TProtocol in, TProtocol out) throws TException {
    processor.process(in, out);
  }
}
