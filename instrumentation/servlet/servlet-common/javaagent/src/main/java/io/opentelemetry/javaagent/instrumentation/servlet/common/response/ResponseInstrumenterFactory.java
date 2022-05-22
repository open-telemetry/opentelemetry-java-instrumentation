/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.util.SpanNames;

public final class ResponseInstrumenterFactory {

  public static Instrumenter<ClassAndMethod, Void> createInstrumenter(String instrumentationName) {
    return Instrumenter.<ClassAndMethod, Void>builder(
            GlobalOpenTelemetry.get(), instrumentationName, SpanNames::fromMethod)
        .newInstrumenter();
  }

  private ResponseInstrumenterFactory() {}
}
