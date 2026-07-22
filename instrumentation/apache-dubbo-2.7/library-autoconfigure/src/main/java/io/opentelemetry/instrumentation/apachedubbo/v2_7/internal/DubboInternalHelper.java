/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboInternalHelper {

  @Nullable
  private static volatile Function<DubboTelemetry, Instrumenter<DubboRequest, Result>>
      serverInstrumenterExtractor;

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

  private DubboInternalHelper() {}
}
