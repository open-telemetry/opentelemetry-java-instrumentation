/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpserver.internal;

import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.httpserver.JdkServerTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JdkInstrumenterBuilderUtil {
  private JdkInstrumenterBuilderUtil() {}

  @Nullable
  private static Function<
          JdkServerTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
      serverBuilderExtractor;

  @Nullable
  public static Function<
          JdkServerTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
      getServerBuilderExtractor() {
    return serverBuilderExtractor;
  }

  public static void setServerBuilderExtractor(
      Function<
              JdkServerTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
          serverBuilderExtractor) {
    JdkInstrumenterBuilderUtil.serverBuilderExtractor = serverBuilderExtractor;
  }
}
