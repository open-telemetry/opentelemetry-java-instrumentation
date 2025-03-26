/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

public interface ApplicationTracerFactory {

  ApplicationTracer newTracer(io.opentelemetry.api.trace.Tracer agentTracer);
}
