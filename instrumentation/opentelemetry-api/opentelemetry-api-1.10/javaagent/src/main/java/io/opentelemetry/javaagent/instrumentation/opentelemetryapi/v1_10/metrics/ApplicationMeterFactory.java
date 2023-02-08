/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

public interface ApplicationMeterFactory {

  ApplicationMeter newMeter(io.opentelemetry.api.metrics.Meter agentMeter);
}
