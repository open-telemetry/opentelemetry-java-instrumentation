/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;

public final class JaxrsConfig {

  public static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "jaxrs", "experimental_span_attributes")
          .orElse(false);

  private JaxrsConfig() {}
}
