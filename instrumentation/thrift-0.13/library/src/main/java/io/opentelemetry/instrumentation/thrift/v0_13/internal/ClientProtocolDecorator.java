/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClientProtocolDecorator extends TProtocolDecorator {

  private final TProtocol protocol;
  private final String serviceName;
  private final Instrumenter<ThriftRequest, ThriftResponse> instrumenter;
  private final ContextPropagators propagators;
  @Nullable private final TTransport transport;
  private final State state;
  private final boolean endSpan;

  public ClientProtocolDecorator(
      TProtocol protocol,
      String serviceName,
      Instrumenter<ThriftRequest, ThriftResponse> instrumenter,
      ContextPropagators propagators) {
    this(protocol, serviceName, instrumenter, propagators, null, new State());
  }

  private ClientProtocolDecorator(
      TProtocol protocol,
      String serviceName,
      Instrumenter<ThriftRequest, ThriftResponse> instrumenter,
      ContextPropagators propagators,
      @Nullable TTransport transport,
      State state) {
    super(protocol);
    this.protocol = protocol;
    this.serviceName = serviceName;
    this.instrumenter = instrumenter;
    this.propagators = propagators;
    this.transport = transport;
    this.state = state;
    // if there's an async callback, the span will be ended in the callback
    this.endSpan = !ClientCallContext.hasAsyncCallback();
    ClientCallContext.setClientProtocolDecorator(this);
  }

  @Override
  public void writeMessageBegin(TMessage message) throws TException {
    Context parentContext = Context.current();
    ThriftRequest request =
        ThriftRequestAccess.newThriftRequest(message.name, serviceName, getSocket());
    if (!instrumenter.shouldStart(parentContext, request)) {
      super.writeMessageBegin(message);
      return;
    }

    state.request = request;
    state.oneWay = message.type == TMessageType.ONEWAY;
    state.contextPropagated = false;
    state.context = instrumenter.start(parentContext, request);

    super.writeMessageBegin(message);

    // we'll try again in readMessageBegin if the socket isn't connected yet
    updateSocket();
  }

  private void updateSocket() {
    if (state.request != null && !state.socketSet) {
      Socket socket = getSocket();
      if (socket != null && socket.isConnected()) {
        state.socketSet = true;
        ThriftRequestAccess.updateSocket(state.request, socket);
      }
    }
  }

  @Nullable
  private Socket getSocket() {
    Socket socket = SocketAccessor.getSocket(super.getTransport());
    // when using async client super.getTransport() returns TMemoryBuffer
    if (socket == null && transport != null) {
      socket = SocketAccessor.getSocket(transport);
    }
    return socket != null && socket.isConnected() ? socket : null;
  }

  @Override
  public void writeMessageEnd() throws TException {
    try {
      super.writeMessageEnd();
    } finally {
      // one way messages won't have a response, so we need to end the span here
      if (state.oneWay && endSpan) {
        endSpan(null);
      }
    }
  }

  @Override
  public void writeFieldStop() throws TException {
    if (!state.contextPropagated && state.context != null) {
      Map<String, String> headers = new HashMap<>();
      propagators
          .getTextMapPropagator()
          .inject(
              state.context,
              headers,
              (carrier, key, value) -> {
                if (carrier != null) {
                  carrier.put(key, value);
                }
              });
      ContextPropagationUtil.writeHeaders(protocol, headers);
      state.contextPropagated = true;
    }

    super.writeFieldStop();
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    try {
      TMessage message = super.readMessageBegin();
      if (message.type == TMessageType.EXCEPTION) {
        state.hasException = true;
      }
      updateSocket();
      return message;
    } catch (TTransportException e) {
      if (endSpan) {
        endSpan(e);
      }
      throw e;
    }
  }

  @Override
  public void readMessageEnd() throws TException {
    try {
      super.readMessageEnd();
    } finally {
      if (endSpan) {
        endSpan(null);
      }
    }
  }

  void endSpan(@Nullable Throwable throwable) {
    // last try to get the socket info if we haven't been able to get it yet
    updateSocket();

    if (state.context != null && state.request != null) {
      instrumenter.end(
          state.context,
          state.request,
          state.hasException ? ThriftResponseAccess.failed() : null,
          throwable);
      state.reset();
    }
  }

  private static class State {
    @Nullable ThriftRequest request;
    @Nullable Context context;
    boolean contextPropagated;
    boolean oneWay;
    boolean hasException;
    boolean socketSet;

    void reset() {
      request = null;
      context = null;
      contextPropagated = false;
      oneWay = false;
      hasException = false;
      socketSet = false;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Factory implements TProtocolFactory {

    private final TProtocolFactory protocolFactory;
    private final String serviceName;
    private final Instrumenter<ThriftRequest, ThriftResponse> instrumenter;
    private final ContextPropagators propagators;
    @Nullable private final TTransport configuredTransport;
    private final State state = new State();

    public Factory(
        TProtocolFactory protocolFactory,
        String serviceName,
        Instrumenter<ThriftRequest, ThriftResponse> instrumenter,
        ContextPropagators propagators,
        @Nullable TTransport transport) {
      this.protocolFactory = protocolFactory;
      this.serviceName = serviceName;
      this.instrumenter = instrumenter;
      this.propagators = propagators;
      this.configuredTransport = transport;
    }

    @Override
    public TProtocol getProtocol(TTransport transport) {
      return new ClientProtocolDecorator(
          protocolFactory.getProtocol(transport),
          serviceName,
          instrumenter,
          propagators,
          configuredTransport,
          state);
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class AgentDecorator {
    private final String serviceName;
    private final Instrumenter<ThriftRequest, ThriftResponse> instrumenter;
    private final ContextPropagators propagators;
    private final State state = new State();

    public AgentDecorator(
        String serviceName,
        Instrumenter<ThriftRequest, ThriftResponse> instrumenter,
        ContextPropagators propagators) {
      this.serviceName = serviceName;
      this.instrumenter = instrumenter;
      this.propagators = propagators;
    }

    public TProtocol decorate(TProtocol protocol) {
      if (protocol instanceof ClientProtocolDecorator) {
        return protocol;
      }
      return new ClientProtocolDecorator(
          protocol, serviceName, instrumenter, propagators, null, state);
    }
  }
}
