/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public class CxfSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxws-cxf-3.0";

  private static final Instrumenter<CxfRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<CxfRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, CxfRequest::spanName)
            .setEnabled(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "common",
                        "controller_telemetry/development",
                        "enabled")
                    .orElse(false))
            .buildInstrumenter();
  }

  public static Instrumenter<CxfRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private CxfSingletons() {}
}
