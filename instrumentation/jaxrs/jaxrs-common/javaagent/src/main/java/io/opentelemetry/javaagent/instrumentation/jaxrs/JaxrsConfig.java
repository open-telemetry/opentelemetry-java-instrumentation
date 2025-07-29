/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class JaxrsConfig {

  public static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.jaxrs.experimental-span-attributes", false);

  private JaxrsConfig() {}
}
