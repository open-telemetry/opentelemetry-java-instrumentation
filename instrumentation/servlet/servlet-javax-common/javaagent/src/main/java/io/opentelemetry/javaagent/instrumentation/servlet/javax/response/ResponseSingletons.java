/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.javax.response;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;

public class ResponseSingletons {

  private static final Instrumenter<ClassAndMethod, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<ClassAndMethod, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.servlet-javax-common",
                SpanNames::fromMethod)
            .newInstrumenter();
  }

  public static Instrumenter<ClassAndMethod, Void> instrumenter() {
    return INSTRUMENTER;
  }
}
