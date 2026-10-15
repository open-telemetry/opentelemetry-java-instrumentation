/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace;

import io.opentelemetry.api.trace.Tracer;

public interface ApplicationTracerFactory {

  ApplicationTracer newTracer(Tracer agentTracer);
}
