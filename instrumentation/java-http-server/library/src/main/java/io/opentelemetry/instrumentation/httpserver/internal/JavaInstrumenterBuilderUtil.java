/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpserver.internal;

import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.httpserver.JavaServerTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JavaInstrumenterBuilderUtil {
  private JavaInstrumenterBuilderUtil() {}

  @Nullable
  private static Function<
          JavaServerTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
      serverBuilderExtractor;

  @Nullable
  public static Function<
          JavaServerTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
      getServerBuilderExtractor() {
    return serverBuilderExtractor;
  }

  public static void setServerBuilderExtractor(
      Function<
              JavaServerTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
          serverBuilderExtractor) {
    JavaInstrumenterBuilderUtil.serverBuilderExtractor = serverBuilderExtractor;
  }
}
