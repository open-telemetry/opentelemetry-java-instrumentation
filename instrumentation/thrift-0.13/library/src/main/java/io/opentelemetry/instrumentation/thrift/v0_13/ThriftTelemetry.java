/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.AsyncMethodCallbackUtil;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ClientCallContext;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ClientProtocolDecorator;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerProcessorDecorator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import javax.annotation.Nullable;
import org.apache.thrift.TProcessor;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

/** Entrypoint for instrumenting Thrift. */
public final class ThriftTelemetry {

  private final Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter;
  private final Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter;
  private final ContextPropagators propagators;

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static ThriftTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static ThriftTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new ThriftTelemetryBuilder(openTelemetry);
  }

  ThriftTelemetry(
      Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter,
      Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter,
      ContextPropagators propagators) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
    this.propagators = propagators;
  }

  /** Returns a {@link TProcessor} that instruments Thrift server requests. */
  public TProcessor wrapServerProcessor(TProcessor delegate, String serviceName) {
    return new ServerProcessorDecorator(delegate, serviceName, serverInstrumenter);
  }

  /** Returns a {@link TProtocol} that instruments Thrift client requests. */
  public TProtocol wrapClientProtocol(TProtocol delegate, String serviceName) {
    return new ClientProtocolDecorator(delegate, serviceName, clientInstrumenter, propagators);
  }

  /** Returns a {@link TProtocolFactory} that instruments Thrift client requests. */
  public TProtocolFactory wrapClientProtocolFactory(
      TProtocolFactory delegate, String serviceName, @Nullable TTransport transport) {
    return new ClientProtocolDecorator.Factory(
        delegate, serviceName, clientInstrumenter, propagators, transport);
  }

  /** Wraps the provided {@link TAsyncClient}, enabling telemetry for it. */
  public <T> T wrapAsyncClient(TAsyncClient client, Class<T> asyncInterface) {
    if (!asyncInterface.isInterface()) {
      throw new IllegalArgumentException(
          "Supplied class must be an interface, but was " + asyncInterface.getName());
    }
    if (!asyncInterface.isInstance(client)) {
      throw new IllegalArgumentException(
          "Client must implement "
              + asyncInterface.getName()
              + ", but was "
              + client.getClass().getName());
    }
    return asyncInterface.cast(
        Proxy.newProxyInstance(
            client.getClass().getClassLoader(),
            new Class<?>[] {asyncInterface},
            (proxy, method, args) -> {
              Throwable error = null;
              ClientCallContext clientContext = ClientCallContext.start();
              try {
                if (args.length > 0 && args[args.length - 1] instanceof AsyncMethodCallback) {
                  AsyncMethodCallback<?> callback = (AsyncMethodCallback<?>) args[args.length - 1];
                  args[args.length - 1] = AsyncMethodCallbackUtil.wrap(callback, clientContext);
                  clientContext.setHasAsyncCallback();
                }
                return method.invoke(client, args);
              } catch (InvocationTargetException e) {
                error = e.getCause();
                throw e.getCause();
              } catch (Throwable t) {
                error = t;
                throw t;
              } finally {
                clientContext.end();
                if (error != null) {
                  clientContext.endSpan(error);
                }
              }
            }));
  }
}
