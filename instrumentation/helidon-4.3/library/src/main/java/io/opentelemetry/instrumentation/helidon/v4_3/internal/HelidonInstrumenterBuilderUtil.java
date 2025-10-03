/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3.internal;

import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.helidon.v4_3.HelidonTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HelidonInstrumenterBuilderUtil {
  private HelidonInstrumenterBuilderUtil() {}

  @Nullable
  private static Function<
          HelidonTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<ServerRequest, ServerResponse>>
      serverBuilderExtractor;

  @Nullable
  public static Function<
          HelidonTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<ServerRequest, ServerResponse>>
      getServerBuilderExtractor() {
    return serverBuilderExtractor;
  }

  public static void setServerBuilderExtractor(
      Function<
              HelidonTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<ServerRequest, ServerResponse>>
          serverBuilderExtractor) {
    HelidonInstrumenterBuilderUtil.serverBuilderExtractor = serverBuilderExtractor;
  }
}
