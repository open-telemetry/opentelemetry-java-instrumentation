/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.clientInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.SocketAccessor;
import io.opentelemetry.instrumentation.thrift.common.ThriftRequest;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.AbstractProtocolWrapper;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;

public final class ClientOutProtocolWrapper extends AbstractProtocolWrapper {
  public static final String ONE_WAY_METHOD_NAME_PREFIX = "recv_";
  private volatile RequestScopeContext requestScopeContext;
  public TTransport transport;
  private boolean injected = true;
  private String methodName;
  private final Set<String> voidMethodNames;
  private String serviceName;
  private byte type = -1;
  private byte originType;

  public ClientOutProtocolWrapper(
      TProtocol protocol, String serviceName, Set<String> voidMethodNames) {
    super(protocol);
    this.serviceName = serviceName;
    this.voidMethodNames = voidMethodNames;
  }

  @Override
  public void writeMessageBegin(TMessage message) throws TException {
    this.injected = false;
    this.methodName = message.name;
    this.originType = message.type;
    // Compatible with version 0.9.1 and 0.9.2 asynchronous logic
    if (message.type == TMessageType.ONEWAY || this.type == -1) {
      this.type = message.type;
    }
    if (!this.isOneway()) {
      if (this.voidMethodNames != null
          && this.voidMethodNames.contains(this.methodName)
          && !this.voidMethodNames.contains(ONE_WAY_METHOD_NAME_PREFIX + this.methodName)) {
        this.type = TMessageType.ONEWAY;
      }
    }
    try {
      if (this.requestScopeContext == null) {
        Socket socket = SocketAccessor.getSocket(super.getTransport());
        if (socket == null) {
          socket = SocketAccessor.getSocket(this.transport);
        }
        ThriftRequest request =
            ThriftRequest.create(this.serviceName, this.methodName, socket, new HashMap<>());
        Context parentContext = Java8BytecodeBridge.currentContext();
        if (!clientInstrumenter().shouldStart(parentContext, request)) {
          return;
        }
        Context context = clientInstrumenter().start(parentContext, request);
        this.requestScopeContext = RequestScopeContext.create(request, null, context);
      }
    } finally {
      if (this.isOneway() && message.type != TMessageType.ONEWAY) {
        // In Thrift 0.9.1 and 0.9.2 versions, the type of the TMessage for oneway requests is still
        // TMessageType.CALL.
        // This causes issues with the server-side instrumentation logic. Here, we are simply
        // correcting the actual request type.
        // Since it is a oneway request, the client does not need to handle the response,
        // and the server does not use this type for any specific logic processing.
        // Therefore, it has no impact on either the client or the server.
        TMessage onewayMessage = new TMessage(message.name, TMessageType.ONEWAY, message.seqid);
        super.writeMessageBegin(onewayMessage);
      } else {
        super.writeMessageBegin(message);
      }
    }
  }

  @Override
  public void writeFieldStop() throws TException {
    try {
      if (!this.injected && this.requestScopeContext != null) {
        ThriftRequest request = this.requestScopeContext.getRequest();
        this.writeHeader(request.getHeader());
      }
    } finally {
      this.injected = true;
      super.writeFieldStop();
    }
  }

  public void writeHeader(Map<String, String> header) throws TException {
    super.writeFieldBegin(new TField(OT_MAGIC_FIELD, TType.MAP, OT_MAGIC_FIELD_ID));
    super.writeMapBegin(new TMap(TType.STRING, TType.STRING, header.size()));

    Set<Map.Entry<String, String>> entries = header.entrySet();
    for (Map.Entry<String, String> entry : entries) {
      super.writeString(entry.getKey());
      super.writeString(entry.getValue());
    }

    super.writeMapEnd();
    super.writeFieldEnd();
  }

  public boolean isOneway() {
    return this.type == TMessageType.ONEWAY;
  }

  public boolean isChangeToOneway() {
    return this.type != this.originType;
  }

  public void updateTransport(TTransport transport) {
    this.transport = transport;
  }

  public RequestScopeContext getRequestScopeContext() {
    return requestScopeContext;
  }

  public void setRequestScopeContext(RequestScopeContext requestScopeContext) {
    this.requestScopeContext = requestScopeContext;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public void setType(byte type) {
    this.type = type;
  }
}
