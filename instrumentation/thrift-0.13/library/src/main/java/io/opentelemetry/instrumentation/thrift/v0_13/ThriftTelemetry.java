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
import io.opentelemetry.instrumentation.thrift.v0_13.internal.SocketAccessor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.apache.thrift.TProcessor;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

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
  public TProtocol wrapClientProtocol(TProtocol delegate) {
    return new ClientProtocolDecorator(delegate, propagators);
  }

  /** Returns a {@link TProtocolFactory} that instruments Thrift client requests. */
  public TProtocolFactory wrapClientProtocolFactory(TProtocolFactory delegate) {
    return new ClientProtocolDecorator.Factory(delegate, propagators);
  }

  /** Wraps the provided {@link TServiceClient}, enabling telemetry for it. */
  public <T> T wrapClient(TServiceClient client, Class<T> clientInterface) {
    if (!clientInterface.isInterface()) {
      throw new IllegalArgumentException(
          "Supplied class must be an interface, but was " + clientInterface.getName());
    }
    if (!clientInterface.isInstance(client)) {
      throw new IllegalArgumentException(
          "Client must implement "
              + clientInterface.getName()
              + ", but was "
              + client.getClass().getName());
    }
    return clientInterface.cast(
        Proxy.newProxyInstance(
            client.getClass().getClassLoader(),
            new Class<?>[] {clientInterface},
            (proxy, method, args) -> {
              Throwable error = null;
              ClientCallContext clientContext =
                  ClientCallContext.start(
                      clientInstrumenter,
                      method.getName(),
                      clientInterface,
                      SocketAccessor.getSocket(client.getInputProtocol().getTransport()));
              try {
                return method.invoke(client, args);
              } catch (InvocationTargetException e) {
                error = e.getCause();
                throw error;
              } catch (Throwable t) {
                error = t;
                throw t;
              } finally {
                clientContext.close();
                clientContext.endSpan(error);
              }
            }));
  }

  /** Wraps the provided {@link TAsyncClient}, enabling telemetry for it. */
  public <T> T wrapAsyncClient(TAsyncClient client, Class<T> clientInterface) {
    if (!clientInterface.isInterface()) {
      throw new IllegalArgumentException(
          "Supplied class must be an interface, but was " + clientInterface.getName());
    }
    if (!clientInterface.isInstance(client)) {
      throw new IllegalArgumentException(
          "Client must implement "
              + clientInterface.getName()
              + ", but was "
              + client.getClass().getName());
    }
    return clientInterface.cast(
        Proxy.newProxyInstance(
            client.getClass().getClassLoader(),
            new Class<?>[] {clientInterface},
            (proxy, method, args) -> {
              Throwable error = null;
              boolean hasAsyncCallback = false;
              ClientCallContext clientContext =
                  ClientCallContext.start(
                      clientInstrumenter,
                      method.getName(),
                      clientInterface,
                      null,
                      SocketAccessor.getSocketAddress(client));
              try {
                if (args.length > 0 && args[args.length - 1] instanceof AsyncMethodCallback) {
                  AsyncMethodCallback<?> callback = (AsyncMethodCallback<?>) args[args.length - 1];
                  args[args.length - 1] = AsyncMethodCallbackUtil.wrap(callback, clientContext);
                  hasAsyncCallback = true;
                }
                return method.invoke(client, args);
              } catch (InvocationTargetException e) {
                error = e.getCause();
                throw error;
              } catch (Throwable t) {
                error = t;
                throw t;
              } finally {
                clientContext.close();
                if (error != null || !hasAsyncCallback) {
                  clientContext.endSpan(error);
                }
              }
            }));
  }
}
