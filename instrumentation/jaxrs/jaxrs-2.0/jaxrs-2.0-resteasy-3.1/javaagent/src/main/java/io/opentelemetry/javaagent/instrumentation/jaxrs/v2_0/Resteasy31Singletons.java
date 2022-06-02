/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsInstrumenterFactory;

public final class Resteasy31Singletons {

  private static final Instrumenter<Jaxrs2HandlerData, Void> INSTANCE =
      JaxrsInstrumenterFactory.createInstrumenter("io.opentelemetry.resteasy-3.1");

  public static Instrumenter<Jaxrs2HandlerData, Void> instrumenter() {
    return INSTANCE;
  }

  private Resteasy31Singletons() {}
}
