/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableLongGauge;

public final class ApplicationObservableLongGauge implements ObservableLongGauge {

  public ApplicationObservableLongGauge(
      io.opentelemetry.api.metrics.ObservableLongGauge agentGauge) {}
}
