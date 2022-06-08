/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.jaxrs.HandlerData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsInstrumenterFactory;

public final class JaxrsAnnotationsSingletons {

  private static final Instrumenter<HandlerData, Void> INSTANCE =
      JaxrsInstrumenterFactory.createInstrumenter("io.opentelemetry.jaxrs-annotations-2.0");

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTANCE;
  }

  private JaxrsAnnotationsSingletons() {}
}
