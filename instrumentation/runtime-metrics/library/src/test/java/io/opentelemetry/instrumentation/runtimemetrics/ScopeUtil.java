/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.util.Optional;

class ScopeUtil {

  static final InstrumentationScopeInfo EXPECTED_SCOPE =
      InstrumentationScopeInfo.builder("io.opentelemetry.runtime-metrics")
          .setVersion(
              Optional.ofNullable(
                      EmbeddedInstrumentationProperties.findVersion(
                          "io.opentelemetry.runtime-metrics"))
                  .orElseThrow(
                      () -> new IllegalStateException("Unable to find instrumentation version")))
          .build();

  private ScopeUtil() {}
}
