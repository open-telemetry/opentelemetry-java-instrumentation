/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import io.opentelemetry.api.trace.Tracer;

public interface ApplicationTracerFactory {

  ApplicationTracer newTracer(Tracer agentTracer);
}
