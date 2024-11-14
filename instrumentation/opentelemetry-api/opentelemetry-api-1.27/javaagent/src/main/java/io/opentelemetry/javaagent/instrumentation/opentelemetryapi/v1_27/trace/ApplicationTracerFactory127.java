/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.trace;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerFactory;

public class ApplicationTracerFactory127 implements ApplicationTracerFactory {

  @Override
  public ApplicationTracer newTracer(Tracer agentTracer) {
    return new ApplicationTracer(agentTracer);
  }
}
