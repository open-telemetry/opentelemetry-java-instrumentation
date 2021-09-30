/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.api.config.ExperimentalConfig;

public class CxfSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxws-2.0-cxf-3.0";

  private static final Instrumenter<CxfRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<CxfRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, CxfRequest::spanName)
            .setDisabled(ExperimentalConfig.get().suppressControllerSpans())
            .newInstrumenter();
  }

  public static Instrumenter<CxfRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private CxfSingletons() {}
}
