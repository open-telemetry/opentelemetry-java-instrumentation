/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0;

import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class SpringWebfluxConfig {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.spring-webflux.experimental-span-attributes", false);

  public static boolean captureExperimentalSpanAttributes() {
    return CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;
  }

  private SpringWebfluxConfig() {}
}
