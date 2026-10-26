/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import javax.annotation.Nullable;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerOutProtocolDecorator extends TProtocolDecorator {

  private static final short APPLICATION_EXCEPTION_TYPE_FIELD_ID = 2;

  private boolean hasException;
  private boolean applicationExceptionMessage;
  private boolean applicationExceptionTypeField;
  private int structDepth;
  @Nullable private String exceptionStatusCode;

  public ServerOutProtocolDecorator(TProtocol protocol) {
    super(protocol);
  }

  public void resetExceptionState() {
    hasException = false;
    applicationExceptionMessage = false;
    applicationExceptionTypeField = false;
    structDepth = 0;
    exceptionStatusCode = null;
  }

  @Override
  public void writeMessageBegin(TMessage message) throws TException {
    if (message.type == TMessageType.EXCEPTION) {
      hasException = true;
    }
    applicationExceptionMessage = message.type == TMessageType.EXCEPTION;
    applicationExceptionTypeField = false;
    structDepth = 0;
    exceptionStatusCode = null;
    super.writeMessageBegin(message);
  }

  @Override
  public void writeStructBegin(TStruct struct) throws TException {
    super.writeStructBegin(struct);
    structDepth++;
    applicationExceptionTypeField = false;
  }

  @Override
  public void writeStructEnd() throws TException {
    try {
      super.writeStructEnd();
    } finally {
      structDepth--;
      applicationExceptionTypeField = false;
    }
  }

  @Override
  public void writeFieldBegin(TField field) throws TException {
    applicationExceptionTypeField = false;
    super.writeFieldBegin(field);
    applicationExceptionTypeField =
        applicationExceptionMessage
            && structDepth == 1
            && field.id == APPLICATION_EXCEPTION_TYPE_FIELD_ID
            && field.type == TType.I32;
  }

  @Override
  public void writeFieldEnd() throws TException {
    try {
      super.writeFieldEnd();
    } finally {
      applicationExceptionTypeField = false;
    }
  }

  @Override
  public void writeI32(int value) throws TException {
    super.writeI32(value);
    if (applicationExceptionTypeField) {
      exceptionStatusCode = ThriftRpcAttributesGetter.applicationExceptionStatusCode(value);
    }
  }

  public boolean hasException() {
    return hasException;
  }

  @Nullable
  public String getExceptionStatusCode() {
    return exceptionStatusCode;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Factory implements TProtocolFactory {

    private final TProtocolFactory protocolFactory;

    public Factory(TProtocolFactory protocolFactory) {
      this.protocolFactory = protocolFactory;
    }

    @Override
    public TProtocol getProtocol(TTransport transport) {
      TProtocol protocol = protocolFactory.getProtocol(transport);
      return new ServerOutProtocolDecorator(protocol);
    }
  }
}
