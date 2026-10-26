/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.thrift.TApplicationException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServerOutProtocolDecoratorTest {

  @ParameterizedTest
  @ValueSource(
      ints = {
        TApplicationException.UNKNOWN,
        TApplicationException.INTERNAL_ERROR,
        TApplicationException.UNSUPPORTED_CLIENT_TYPE
      })
  void applicationExceptionStatusCode(int code) throws Exception {
    ServerOutProtocolDecorator protocol = newProtocol();
    protocol.writeMessageBegin(new TMessage("method", TMessageType.EXCEPTION, 1));
    new TApplicationException(code).write(protocol);

    assertThat(protocol.hasException()).isTrue();
    assertThat(protocol.getExceptionStatusCode()).isEqualTo(Integer.toString(code));
  }

  @Test
  void unknownApplicationExceptionStatusCodeIsUnavailable() throws Exception {
    ServerOutProtocolDecorator protocol = newProtocol();
    protocol.writeMessageBegin(new TMessage("method", TMessageType.EXCEPTION, 1));
    new TApplicationException(11).write(protocol);

    assertThat(protocol.hasException()).isTrue();
    assertThat(protocol.getExceptionStatusCode()).isNull();
  }

  @Test
  void missingOrNestedStatusFieldIsUnavailable() throws Exception {
    ServerOutProtocolDecorator protocol = newProtocol();
    protocol.writeMessageBegin(new TMessage("method", TMessageType.EXCEPTION, 1));
    protocol.writeStructBegin(new TStruct("TApplicationException"));
    protocol.writeFieldBegin(new TField("details", TType.STRUCT, (short) 1));
    protocol.writeStructBegin(new TStruct("details"));
    protocol.writeFieldBegin(new TField("type", TType.I32, (short) 2));
    protocol.writeI32(TApplicationException.INTERNAL_ERROR);
    protocol.writeFieldEnd();
    protocol.writeFieldStop();
    protocol.writeStructEnd();
    protocol.writeFieldEnd();
    protocol.writeFieldStop();
    protocol.writeStructEnd();
    protocol.writeMessageEnd();

    assertThat(protocol.getExceptionStatusCode()).isNull();
  }

  @Test
  void replyFieldIsNotApplicationExceptionStatus() throws Exception {
    ServerOutProtocolDecorator protocol = newProtocol();
    protocol.writeMessageBegin(new TMessage("method", TMessageType.REPLY, 1));
    protocol.writeStructBegin(new TStruct("Method_result"));
    protocol.writeFieldBegin(new TField("businessError", TType.I32, (short) 2));
    protocol.writeI32(TApplicationException.INTERNAL_ERROR);
    protocol.writeFieldEnd();
    protocol.writeFieldStop();
    protocol.writeStructEnd();
    protocol.writeMessageEnd();

    assertThat(protocol.hasException()).isFalse();
    assertThat(protocol.getExceptionStatusCode()).isNull();
  }

  @Test
  void resetExceptionStateClearsStatus() throws Exception {
    ServerOutProtocolDecorator protocol = newProtocol();
    protocol.writeMessageBegin(new TMessage("method", TMessageType.EXCEPTION, 1));
    new TApplicationException(TApplicationException.INTERNAL_ERROR).write(protocol);

    protocol.resetExceptionState();

    assertThat(protocol.hasException()).isFalse();
    assertThat(protocol.getExceptionStatusCode()).isNull();
  }

  private static ServerOutProtocolDecorator newProtocol() {
    return new ServerOutProtocolDecorator(new TBinaryProtocol(new TMemoryBuffer(1024)));
  }
}
