/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.cxf.v3_2;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxrs.HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsInstrumenterFactory;

public class CxfSingletons {

  private static final Instrumenter<HandlerData, Void> instrumenter =
      JaxrsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxrs-2.0-cxf-3.2");

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return instrumenter;
  }

  private CxfSingletons() {}
}
