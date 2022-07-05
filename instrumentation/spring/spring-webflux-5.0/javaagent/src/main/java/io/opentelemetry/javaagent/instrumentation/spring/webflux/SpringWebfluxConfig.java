/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux;

import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public class SpringWebfluxConfig {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.spring-webflux.experimental-span-attributes", false);

  public static boolean captureExperimentalSpanAttributes() {
    return CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;
  }
}
