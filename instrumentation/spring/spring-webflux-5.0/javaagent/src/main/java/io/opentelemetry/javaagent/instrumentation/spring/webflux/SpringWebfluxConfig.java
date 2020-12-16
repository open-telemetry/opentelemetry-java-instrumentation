/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux;

import io.opentelemetry.instrumentation.api.config.Config;

public class SpringWebfluxConfig {

  public static boolean captureExperimentalSpanAttributes() {
    return Config.get()
        .getBooleanProperty(
            "otel.instrumentation.spring-webflux.experimental-span-attributes", false);
  }
}
