/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static java.util.logging.Level.FINE;

import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  private static final Logger logger = Logger.getLogger(Experimental.class.getName());

  @Nullable private static final Method interceptWithTargetMethod;
  @Nullable private static final Class<?> interceptorFactoryClass;

  static {
    Method method = null;
    Class<?> factoryClass = null;
    try {
      ClassLoader classLoader = ManagedChannelBuilder.class.getClassLoader();
      Class<?> internalBuilder =
          Class.forName("io.grpc.InternalManagedChannelBuilder", false, classLoader);
      factoryClass =
          Class.forName(
              "io.grpc.InternalManagedChannelBuilder$InternalInterceptorFactory",
              false,
              classLoader);
      method =
          internalBuilder.getMethod(
              "interceptWithTarget", ManagedChannelBuilder.class, factoryClass);
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      // The internal target-aware interception API is available since gRPC 1.64.0.
    }
    interceptWithTargetMethod = method;
    interceptorFactoryClass = factoryClass;
  }

  /**
   * Adds a tracing interceptor that automatically captures the channel's target for {@code
   * server.address} and {@code server.port}.
   *
   * <p>This method depends on gRPC's internal {@code
   * InternalManagedChannelBuilder.interceptWithTarget} API, available since gRPC 1.64.0. That API
   * may change or disappear in any gRPC release. When it is unavailable or the builder does not
   * support it, this method falls back to the channel's authority.
   *
   * <p>Use {@link GrpcTelemetry#createClientInterceptor(String)} to supply the target without
   * depending on this internal gRPC API.
   */
  public static void addClientInterceptor(
      GrpcTelemetry telemetry, ManagedChannelBuilder<?> builder) {
    if (interceptWithTargetMethod != null && interceptorFactoryClass != null) {
      try {
        interceptWithTargetMethod.invoke(null, builder, newInterceptorFactory(telemetry));
        return;
      } catch (Exception e) {
        logger.log(FINE, "Failed to use interceptWithTarget, falling back to channel authority", e);
      }
    }
    builder.intercept(telemetry.createClientInterceptor());
  }

  private static Object newInterceptorFactory(GrpcTelemetry telemetry) {
    return Proxy.newProxyInstance(
        ManagedChannelBuilder.class.getClassLoader(),
        new Class<?>[] {interceptorFactoryClass},
        (proxy, method, args) -> {
          if ("newInterceptor".equals(method.getName())) {
            return Internal.createClientInterceptor(telemetry, (String) args[0]);
          }
          switch (method.getName()) {
            case "equals":
              return proxy == args[0];
            case "hashCode":
              return System.identityHashCode(proxy);
            case "toString":
              return "GrpcInterceptorFactory";
            default:
              throw new UnsupportedOperationException(method.toString());
          }
        });
  }

  private Experimental() {}
}
