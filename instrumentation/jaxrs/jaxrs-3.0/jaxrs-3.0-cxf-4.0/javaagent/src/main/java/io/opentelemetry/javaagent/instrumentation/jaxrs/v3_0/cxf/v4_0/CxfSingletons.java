/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.cxf.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxrs.common.HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.common.JaxrsInstrumenterFactory;

class CxfSingletons {

  private static final Instrumenter<HandlerData, Void> instrumenter =
      JaxrsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxrs-3.0-cxf-4.0");

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return instrumenter;
  }

  private CxfSingletons() {}
}
