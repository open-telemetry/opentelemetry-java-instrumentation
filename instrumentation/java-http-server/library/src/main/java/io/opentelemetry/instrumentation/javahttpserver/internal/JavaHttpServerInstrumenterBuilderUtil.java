/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver.internal;

import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.javahttpserver.JavaHttpServerTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JavaHttpServerInstrumenterBuilderUtil {
  private JavaHttpServerInstrumenterBuilderUtil() {}

  @Nullable
  private static Function<
          JavaHttpServerTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
      serverBuilderExtractor;

  @Nullable
  public static Function<
          JavaHttpServerTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
      getServerBuilderExtractor() {
    return serverBuilderExtractor;
  }

  public static void setServerBuilderExtractor(
      Function<
              JavaHttpServerTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange>>
          serverBuilderExtractor) {
    JavaHttpServerInstrumenterBuilderUtil.serverBuilderExtractor = serverBuilderExtractor;
  }
}
