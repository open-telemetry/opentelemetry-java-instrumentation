/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import java.net.Socket;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TType;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerInProtocolDecorator extends TProtocolDecorator {

  private final TProtocol protocol;
  private final Instrumenter<ThriftRequest, ThriftResponse> instrumenter;
  private final String serviceName;

  @Nullable private String methodName;
  @Nullable private ThriftRequest currentRequest;
  @Nullable private Context currentContext;
  @Nullable private Scope currentScope;

  public ServerInProtocolDecorator(
      TProtocol protocol,
      String serviceName,
      Instrumenter<ThriftRequest, ThriftResponse> instrumenter) {
    super(protocol);
    this.protocol = protocol;
    this.serviceName = serviceName;
    this.instrumenter = instrumenter;
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    TMessage message = super.readMessageBegin();
    this.methodName = message.name;
    return message;
  }

  @Override
  public TField readFieldBegin() throws TException {
    TField field = super.readFieldBegin();
    // start span when context propagation field is read, if the message doesn't include context
    // propagation field, span will be started in readMessageEnd()
    if (field.id == ContextPropagationUtil.TRACE_CONTEXT_FIELD_ID && field.type == TType.MAP) {
      Map<String, String> headers = ContextPropagationUtil.readHeaders(protocol);
      super.readFieldEnd();

      Socket socket = SocketAccessor.getSocket(super.getTransport());
      if (socket == null) {
        // for non-blocking server, the socket may not be available through super.getTransport()
        socket = SocketAccessor.getSocket(ServerCallContext.getTransport());
      }
      ThriftRequest request = new ThriftRequest(methodName, serviceName, socket, headers);
      Context parentContext = Context.current();
      if (!instrumenter.shouldStart(parentContext, request)) {
        // proceed to the next field
        return this.readFieldBegin();
      }
      currentRequest = request;
      currentContext = instrumenter.start(parentContext, request);

      // proceed to the next field
      return this.readFieldBegin();
    }
    return field;
  }

  @Override
  public void readMessageEnd() throws TException {
    super.readMessageEnd();
    // message didn't include context propagation field
    if (currentContext == null) {
      // for non-blocking server, the socket may not be available through super.getTransport()
      Socket socket = SocketAccessor.getSocket(super.getTransport());
      ThriftRequest request = new ThriftRequest(this.methodName, this.serviceName, socket);
      Context parentContext = Context.current();
      if (!instrumenter.shouldStart(parentContext, request)) {
        return;
      }
      currentRequest = request;
      currentContext = instrumenter.start(parentContext, request);
    }
    currentScope = currentContext.makeCurrent();
  }

  public void endSpan(@Nullable Throwable throwable, boolean failed) {
    if (currentScope == null || currentContext == null || currentRequest == null) {
      return;
    }
    currentScope.close();

    instrumenter.end(
        currentContext, currentRequest, failed ? ThriftResponse.FAILED : null, throwable);
  }
}
