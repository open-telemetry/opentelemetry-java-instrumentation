/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;
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
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.function.UnaryOperator;
import org.apache.thrift.protocol.TProtocol;

public final class ThriftSingletons {

  private static final VirtualField<TProtocol, TProtocol> PROTOCOL_DECORATOR =
      VirtualField.find(TProtocol.class, TProtocol.class);
  private static final Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter =
      ThriftInstrumenterFactory.createClientInstrumenter(
          GlobalOpenTelemetry.get(), UnaryOperator.identity(), emptyList(), emptyList());
  private static final Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter =
      ThriftInstrumenterFactory.createServerInstrumenter(
          GlobalOpenTelemetry.get(), UnaryOperator.identity(), emptyList(), emptyList());
  private static final ContextPropagators propagators = GlobalOpenTelemetry.get().getPropagators();
  private static final boolean useIdlServiceName = emitPreviewRpcSemconv(GlobalOpenTelemetry.get());
  private static final ClassValue<String> serviceNames =
      new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
          for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Class<?> service = current.getEnclosingClass();
            if (service == null) {
              continue;
            }
            // Generated processors bind I to their service's Iface or AsyncIface.
            for (TypeVariable<?> parameter : current.getTypeParameters()) {
              for (Type bound : parameter.getBounds()) {
                if (bound instanceof Class<?>) {
                  Class<?> iface = (Class<?>) bound;
                  if (iface.isInterface()
                      && iface.getEnclosingClass() == service
                      && ("Iface".equals(iface.getSimpleName())
                          || "AsyncIface".equals(iface.getSimpleName()))) {
                    return service.getName();
                  }
                }
              }
            }
          }
          return "";
        }
      };

  public static String getServerServiceName(Object processor, Object iface) {
    if (useIdlServiceName) {
      String serviceName = serviceNames.get(processor.getClass());
      if (!serviceName.isEmpty()) {
        return serviceName;
      }
    }
    return iface.getClass().getName();
  }

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
    TProtocol protocolDecorator = PROTOCOL_DECORATOR.get(protocol);
    if (protocolDecorator != null) {
      return protocolDecorator;
    }

    protocolDecorator =
        isOutput
            ? new ServerOutProtocolDecorator(protocol)
            : new ServerInProtocolDecorator(protocol, null, serverInstrumenter());
    PROTOCOL_DECORATOR.set(protocol, protocolDecorator);
    return protocolDecorator;
  }

  private ThriftSingletons() {}
}
