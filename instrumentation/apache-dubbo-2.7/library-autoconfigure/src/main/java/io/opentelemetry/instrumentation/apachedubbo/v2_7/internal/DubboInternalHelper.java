/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.net.InetSocketAddress;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class DubboInternalHelper {

  @Nullable
  private static volatile Function<DubboTelemetry, Instrumenter<DubboRequest, Result>>
      serverInstrumenterExtractor;

  @Nullable private static volatile UnknownServiceRequestFactory unknownServiceRequestFactory;

  public static void setServerInstrumenterExtractor(
      Function<DubboTelemetry, Instrumenter<DubboRequest, Result>> extractor) {
    serverInstrumenterExtractor = extractor;
  }

  @Nullable
  public static Instrumenter<DubboRequest, Result> getServerInstrumenter(DubboTelemetry telemetry) {
    Function<DubboTelemetry, Instrumenter<DubboRequest, Result>> extractor =
        serverInstrumenterExtractor;
    return extractor != null ? extractor.apply(telemetry) : null;
  }

  public static void setUnknownServiceRequestFactory(UnknownServiceRequestFactory factory) {
    unknownServiceRequestFactory = factory;
  }

  public static DubboRequest createForUnknownService(
      RpcInvocation invocation,
      String originalFullMethodName,
      @Nullable InetSocketAddress remoteAddress) {
    UnknownServiceRequestFactory factory = unknownServiceRequestFactory;
    if (factory == null) {
      throw new IllegalStateException("Unknown service request factory has not been initialized");
    }
    return factory.create(invocation, originalFullMethodName, remoteAddress);
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  @FunctionalInterface
  public interface UnknownServiceRequestFactory {
    DubboRequest create(
        RpcInvocation invocation,
        String originalFullMethodName,
        @Nullable InetSocketAddress remoteAddress);
  }

  private DubboInternalHelper() {}
}
