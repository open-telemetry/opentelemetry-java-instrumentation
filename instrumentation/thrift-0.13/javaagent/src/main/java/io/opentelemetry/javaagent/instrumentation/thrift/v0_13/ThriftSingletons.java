/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerInProtocolDecorator;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerOutProtocolDecorator;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ThriftInstrumenterFactory;
import java.util.function.UnaryOperator;
import org.apache.thrift.protocol.TProtocol;

public final class ThriftSingletons {

  private static final VirtualField<TProtocol, TProtocol> protocolVirtualField =
      VirtualField.find(TProtocol.class, TProtocol.class);
  private static final Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter =
      ThriftInstrumenterFactory.createClientInstrumenter(
          GlobalOpenTelemetry.get(), UnaryOperator.identity(), emptyList(), emptyList());
  private static final Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter =
      ThriftInstrumenterFactory.createServerInstrumenter(
          GlobalOpenTelemetry.get(), UnaryOperator.identity(), emptyList(), emptyList());
  private static final ContextPropagators propagators = GlobalOpenTelemetry.get().getPropagators();

  public static Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter() {
    return clientInstrumenter;
  }

  public static Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter() {
    return serverInstrumenter;
  }

  public static ContextPropagators propagators() {
    return propagators;
  }

  public static TProtocol getProtocolDecorator(TProtocol protocol, boolean isOutput) {
    TProtocol protocolDecorator = protocolVirtualField.get(protocol);
    if (protocolDecorator != null) {
      return protocolDecorator;
    }

    protocolDecorator =
        isOutput
            ? new ServerOutProtocolDecorator(protocol)
            : new ServerInProtocolDecorator(protocol, null, serverInstrumenter());
    protocolVirtualField.set(protocol, protocolDecorator);
    return protocolDecorator;
  }

  private ThriftSingletons() {}
}
