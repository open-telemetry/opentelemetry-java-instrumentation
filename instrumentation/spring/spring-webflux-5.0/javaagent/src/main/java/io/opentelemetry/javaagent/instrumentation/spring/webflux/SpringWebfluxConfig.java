/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux;

import io.opentelemetry.instrumentation.api.config.Config;

public class SpringWebfluxConfig {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBoolean("otel.instrumentation.spring-webflux.experimental-span-attributes", false);

  public static boolean captureExperimentalSpanAttributes() {
    return CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;
  }
}
