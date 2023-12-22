/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.InstrumentationScopeInfoBuilder;
import javax.annotation.Nullable;

public final class ScopeUtil {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.runtime-telemetry-java8";

  @Nullable
  private static final String INSTRUMENTATION_VERSION =
      EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);

  public static final InstrumentationScopeInfo EXPECTED_SCOPE;

  static {
    InstrumentationScopeInfoBuilder builder =
        InstrumentationScopeInfo.builder(INSTRUMENTATION_NAME);
    if (INSTRUMENTATION_VERSION != null) {
      builder.setVersion(INSTRUMENTATION_VERSION);
    }
    EXPECTED_SCOPE = builder.build();
  }

  private ScopeUtil() {}
}
