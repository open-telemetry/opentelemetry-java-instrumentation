/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.cxf.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

class CxfSingletons {
  // version file is generated from the gradle module name; look it up explicitly so the legacy
  // scope name still resolves to a version
  private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.jaxws-2.0-cxf-3.0";

  private static final String INSTRUMENTATION_NAME =
      AgentCommonConfig.get().isV3Preview()
          ? VERSION_LOOKUP_NAME
          : "io.opentelemetry.jaxws-cxf-3.0";

  private static final Instrumenter<CxfRequest, Void> instrumenter;

  static {
    InstrumenterBuilder<CxfRequest, Void> builder =
        Instrumenter.<CxfRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, CxfRequest::spanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled());
    String version = EmbeddedInstrumentationProperties.findVersion(VERSION_LOOKUP_NAME);
    if (version != null) {
      builder.setInstrumentationVersion(version);
    }
    instrumenter = builder.buildInstrumenter();
  }

  static Instrumenter<CxfRequest, Void> instrumenter() {
    return instrumenter;
  }

  private CxfSingletons() {}
}
