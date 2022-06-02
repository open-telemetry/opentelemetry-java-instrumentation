/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsInstrumenterFactory;

public final class ResteasySingletons {

  private static final Instrumenter<Jaxrs3HandlerData, Void> INSTANCE =
      JaxrsInstrumenterFactory.createInstrumenter("io.opentelemetry.resteasy-6.0");

  public static Instrumenter<Jaxrs3HandlerData, Void> instrumenter() {
    return INSTANCE;
  }

  private ResteasySingletons() {}
}
