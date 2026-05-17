/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.context.propagation.ContextPropagators;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.transport.TTransport;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClientProtocolDecorator extends TProtocolDecorator {

  private final TProtocol protocol;
  private final ContextPropagators propagators;
  private final Supplier<ClientCallContext> callContextSupplier;
  @Nullable private String currentMessageName;
  private int structDepth;
  private boolean writingRequestArgsStruct;

  public ClientProtocolDecorator(TProtocol protocol, ContextPropagators propagators) {
    this(protocol, propagators, ClientCallContext::get);
  }

  private ClientProtocolDecorator(
      TProtocol protocol,
      ContextPropagators propagators,
      Supplier<ClientCallContext> callContextSupplier) {
    super(protocol);
    this.protocol = protocol;
    this.propagators = propagators;
    this.callContextSupplier = callContextSupplier;
  }

  @Override
  public void writeMessageBegin(TMessage message) throws TException {
    super.writeMessageBegin(message);
    currentMessageName = message.name;
    structDepth = 0;
    writingRequestArgsStruct = false;
  }

  @Override
  public void writeStructBegin(TStruct struct) throws TException {
    super.writeStructBegin(struct);
    if (structDepth == 0) {
      writingRequestArgsStruct = isRequestArgsStruct(struct.name);
    }
    structDepth++;
  }

  @Override
  public void writeStructEnd() throws TException {
    try {
      super.writeStructEnd();
    } finally {
      structDepth--;
      if (structDepth == 0) {
        writingRequestArgsStruct = false;
      }
    }
  }

  @Override
  public void writeMessageEnd() throws TException {
    try {
      super.writeMessageEnd();
    } finally {
      currentMessageName = null;
      structDepth = 0;
      writingRequestArgsStruct = false;
    }
  }

  @Override
  public void writeFieldStop() throws TException {
    ClientCallContext callContext = callContextSupplier.get();
    if (isTopLevelRequestArgsStruct()
        && callContext != null
        && !callContext.contextPropagated
        && callContext.context != null) {
      Map<String, String> headers = new HashMap<>();
      propagators
          .getTextMapPropagator()
          .inject(
              callContext.context,
              headers,
              (carrier, key, value) -> {
                if (carrier != null) {
                  carrier.put(key, value);
                }
              });
      ContextPropagationUtil.writeHeaders(protocol, headers);
      callContext.contextPropagated = true;
    }

    super.writeFieldStop();
  }

  private boolean isTopLevelRequestArgsStruct() {
    return structDepth == 1 && writingRequestArgsStruct;
  }

  private boolean isRequestArgsStruct(String structName) {
    return currentMessageName != null && (currentMessageName + "_args").equals(structName);
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Factory implements TProtocolFactory {

    private final TProtocolFactory protocolFactory;
    private final ContextPropagators propagators;

    public Factory(TProtocolFactory protocolFactory, ContextPropagators propagators) {
      this.protocolFactory = protocolFactory;
      this.propagators = propagators;
    }

    @Override
    public TProtocol getProtocol(TTransport transport) {
      TProtocol protocol = protocolFactory.getProtocol(transport);
      ClientCallContext context = ClientCallContext.get();
      if (context == null) {
        return protocol;
      }
      return new ClientProtocolDecorator(protocol, propagators, () -> context);
    }
  }
}
