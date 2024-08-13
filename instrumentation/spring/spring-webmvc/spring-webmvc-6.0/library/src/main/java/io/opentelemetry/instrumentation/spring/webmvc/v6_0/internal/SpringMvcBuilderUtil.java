/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0.internal;

import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.spring.webmvc.v6_0.SpringWebMvcTelemetryBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SpringMvcBuilderUtil {
  private SpringMvcBuilderUtil() {}

  // allows access to the private field for the spring starter
  private static Function<
          SpringWebMvcTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
      builderExtractor;

  public static Function<
          SpringWebMvcTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
      getBuilderExtractor() {
    return builderExtractor;
  }

  public static void setBuilderExtractor(
      Function<
              SpringWebMvcTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
          builderExtractor) {
    SpringMvcBuilderUtil.builderExtractor = builderExtractor;
  }
}
