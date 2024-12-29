/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerFactory;

// this class is used from opentelemetry-api-1.27.0 via reflection
public class ApplicationTracerFactory140Incubator implements ApplicationTracerFactory {

  @Override
  public ApplicationTracer newTracer(Tracer agentTracer) {
    return new ApplicationTracer140Incubator(agentTracer);
  }
}
