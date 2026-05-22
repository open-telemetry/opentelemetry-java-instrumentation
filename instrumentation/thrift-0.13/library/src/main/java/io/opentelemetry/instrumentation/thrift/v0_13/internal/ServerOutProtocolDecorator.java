/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerOutProtocolDecorator extends TProtocolDecorator {

  private boolean hasException;

  public ServerOutProtocolDecorator(TProtocol protocol) {
    super(protocol);
  }

  @Override
  public void writeMessageBegin(TMessage message) throws TException {
    if (message.type == TMessageType.EXCEPTION) {
      hasException = true;
    }
    super.writeMessageBegin(message);
  }

  public boolean hasException() {
    return hasException;
  }
}
