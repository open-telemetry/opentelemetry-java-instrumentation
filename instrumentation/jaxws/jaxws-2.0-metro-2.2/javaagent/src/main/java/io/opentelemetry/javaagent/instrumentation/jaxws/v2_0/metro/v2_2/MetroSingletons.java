/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.metro.v2_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

class MetroSingletons {
  // version file is generated from the gradle module name; look it up explicitly so the legacy
  // scope name still resolves to a version
  private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.jaxws-2.0-metro-2.2";

  private static final String INSTRUMENTATION_NAME =
      AgentCommonConfig.get().isV3Preview()
          ? VERSION_LOOKUP_NAME
          : "io.opentelemetry.jaxws-metro-2.2";

  private static final Instrumenter<MetroRequest, Void> instrumenter;

  static {
    InstrumenterBuilder<MetroRequest, Void> builder =
        Instrumenter.<MetroRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, MetroRequest::spanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled());
    String version = EmbeddedInstrumentationProperties.findVersion(VERSION_LOOKUP_NAME);
    if (version != null) {
      builder.setInstrumentationVersion(version);
    }
    instrumenter = builder.buildInstrumenter();
  }

  static Instrumenter<MetroRequest, Void> instrumenter() {
    return instrumenter;
  }

  private MetroSingletons() {}
}
