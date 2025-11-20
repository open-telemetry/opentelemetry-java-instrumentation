/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.serverInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.SocketAccessor;
import io.opentelemetry.instrumentation.thrift.common.ThriftRequest;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.AbstractProtocolWrapper;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;

public final class ServerInProtocolWrapper extends AbstractProtocolWrapper {

  private volatile RequestScopeContext requestScopeContext;
  private String methodName;
  private String serviceName;
  public TTransport transport;
  private byte type;
  private Timer timer;

  public ServerInProtocolWrapper(TProtocol protocol) {
    super(protocol);
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    TMessage message = super.readMessageBegin();
    this.methodName = message.name;
    this.type = message.type;
    this.timer = Timer.start();
    return message;
  }

  @Override
  public TField readFieldBegin() throws TException {
    TField field = super.readFieldBegin();
    if (field.id == OT_MAGIC_FIELD_ID && field.type == TType.MAP) {
      try {
        TMap map = super.readMapBegin();
        Map<String, String> header = new HashMap<>(map.size);

        for (int i = 0; i < map.size; i++) {
          header.put(readString(), readString());
        }

        Socket socket = SocketAccessor.getSocket(super.getTransport());
        if (socket == null) {
          // The non-blocking processing method cannot obtain the corresponding Transport with a
          // socket through super.getTransport().
          // Instrumentation has been added to the invoke methods of FrameBuffer and
          // AsyncFrameBuffer to actively set TNonblockingTransport.
          // This serves as a compensation here.
          socket = SocketAccessor.getSocket(this.transport);
        }
        ThriftRequest request =
            ThriftRequest.create(this.serviceName, this.methodName, socket, header);
        Context parentContext = Java8BytecodeBridge.currentContext();
        if (!serverInstrumenter().shouldStart(parentContext, request)) {
          return field;
        }
        Context context = serverInstrumenter().start(parentContext, request);
        this.requestScopeContext = RequestScopeContext.create(request, null, context);
      } finally {
        super.readMapEnd();
        super.readFieldEnd();
      }
      return this.readFieldBegin();
    }
    return field;
  }

  @Override
  public void readMessageEnd() throws TException {
    super.readMessageEnd();
    if (this.requestScopeContext == null) {
      Socket socket = SocketAccessor.getSocket(super.getTransport());
      if (socket == null) {
        // The non-blocking processing method cannot obtain the corresponding Transport with a
        // socket through super.getTransport().
        // Instrumentation has been added to the invoke methods of FrameBuffer and AsyncFrameBuffer
        // to actively set TNonblockingTransport.
        // This serves as a compensation here.
        socket = SocketAccessor.getSocket(this.transport);
      }
      ThriftRequest request =
          ThriftRequest.create(this.serviceName, this.methodName, socket, new HashMap<>());
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!serverInstrumenter().shouldStart(parentContext, request)) {
        return;
      }
      Context context = serverInstrumenter().start(parentContext, request);
      Scope scope = context.makeCurrent();
      this.requestScopeContext = RequestScopeContext.create(request, scope, context);
    }
  }

  public String getMethodName() {
    return methodName;
  }

  public boolean isOneway() {
    return type == TMessageType.ONEWAY;
  }

  public RequestScopeContext getRequestScopeContext() {
    return requestScopeContext;
  }

  public void setRequestScopeContext(RequestScopeContext requestScopeContext) {
    this.requestScopeContext = requestScopeContext;
  }

  public Timer getTimer() {
    return timer;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public void setTransport(TTransport transport) {
    this.transport = transport;
  }
}
