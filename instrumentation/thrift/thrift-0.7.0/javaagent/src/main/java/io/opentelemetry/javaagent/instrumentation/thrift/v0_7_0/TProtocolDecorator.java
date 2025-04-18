/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

import java.nio.ByteBuffer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TSet;
import org.apache.thrift.protocol.TStruct;

@SuppressWarnings("all")
public abstract class TProtocolDecorator extends TProtocol {

  private final TProtocol concreteProtocol;

  /**
   * Encloses the specified protocol.
   *
   * @param protocol All operations will be forward to this protocol. Must be non-null.
   */
  public TProtocolDecorator(TProtocol protocol) {
    super(protocol.getTransport());
    concreteProtocol = protocol;
  }

  @Override
  public void writeMessageBegin(TMessage tMessage) throws TException {
    concreteProtocol.writeMessageBegin(tMessage);
  }

  @Override
  public void writeMessageEnd() throws TException {
    concreteProtocol.writeMessageEnd();
  }

  @Override
  public void writeStructBegin(TStruct tStruct) throws TException {
    concreteProtocol.writeStructBegin(tStruct);
  }

  @Override
  public void writeStructEnd() throws TException {
    concreteProtocol.writeStructEnd();
  }

  @Override
  public void writeFieldBegin(TField tField) throws TException {
    concreteProtocol.writeFieldBegin(tField);
  }

  @Override
  public void writeFieldEnd() throws TException {
    concreteProtocol.writeFieldEnd();
  }

  @Override
  public void writeFieldStop() throws TException {
    concreteProtocol.writeFieldStop();
  }

  @Override
  public void writeMapBegin(TMap tMap) throws TException {
    concreteProtocol.writeMapBegin(tMap);
  }

  @Override
  public void writeMapEnd() throws TException {
    concreteProtocol.writeMapEnd();
  }

  @Override
  public void writeListBegin(TList tList) throws TException {
    concreteProtocol.writeListBegin(tList);
  }

  @Override
  public void writeListEnd() throws TException {
    concreteProtocol.writeListEnd();
  }

  @Override
  public void writeSetBegin(TSet tSet) throws TException {
    concreteProtocol.writeSetBegin(tSet);
  }

  @Override
  public void writeSetEnd() throws TException {
    concreteProtocol.writeSetEnd();
  }

  @Override
  public void writeBool(boolean b) throws TException {
    concreteProtocol.writeBool(b);
  }

  @Override
  public void writeByte(byte b) throws TException {
    concreteProtocol.writeByte(b);
  }

  @Override
  public void writeI16(short i) throws TException {
    concreteProtocol.writeI16(i);
  }

  @Override
  public void writeI32(int i) throws TException {
    concreteProtocol.writeI32(i);
  }

  @Override
  public void writeI64(long l) throws TException {
    concreteProtocol.writeI64(l);
  }

  @Override
  public void writeDouble(double v) throws TException {
    concreteProtocol.writeDouble(v);
  }

  @Override
  public void writeString(String s) throws TException {
    concreteProtocol.writeString(s);
  }

  @Override
  public void writeBinary(ByteBuffer buf) throws TException {
    concreteProtocol.writeBinary(buf);
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    return concreteProtocol.readMessageBegin();
  }

  @Override
  public void readMessageEnd() throws TException {
    concreteProtocol.readMessageEnd();
  }

  @Override
  public TStruct readStructBegin() throws TException {
    return concreteProtocol.readStructBegin();
  }

  @Override
  public void readStructEnd() throws TException {
    concreteProtocol.readStructEnd();
  }

  @Override
  public TField readFieldBegin() throws TException {
    return concreteProtocol.readFieldBegin();
  }

  @Override
  public void readFieldEnd() throws TException {
    concreteProtocol.readFieldEnd();
  }

  @Override
  public TMap readMapBegin() throws TException {
    return concreteProtocol.readMapBegin();
  }

  @Override
  public void readMapEnd() throws TException {
    concreteProtocol.readMapEnd();
  }

  @Override
  public TList readListBegin() throws TException {
    return concreteProtocol.readListBegin();
  }

  @Override
  public void readListEnd() throws TException {
    concreteProtocol.readListEnd();
  }

  @Override
  public TSet readSetBegin() throws TException {
    return concreteProtocol.readSetBegin();
  }

  @Override
  public void readSetEnd() throws TException {
    concreteProtocol.readSetEnd();
  }

  @Override
  public boolean readBool() throws TException {
    return concreteProtocol.readBool();
  }

  @Override
  public byte readByte() throws TException {
    return concreteProtocol.readByte();
  }

  @Override
  public short readI16() throws TException {
    return concreteProtocol.readI16();
  }

  @Override
  public int readI32() throws TException {
    return concreteProtocol.readI32();
  }

  @Override
  public long readI64() throws TException {
    return concreteProtocol.readI64();
  }

  @Override
  public double readDouble() throws TException {
    return concreteProtocol.readDouble();
  }

  @Override
  public String readString() throws TException {
    return concreteProtocol.readString();
  }

  @Override
  public ByteBuffer readBinary() throws TException {
    return concreteProtocol.readBinary();
  }
}
