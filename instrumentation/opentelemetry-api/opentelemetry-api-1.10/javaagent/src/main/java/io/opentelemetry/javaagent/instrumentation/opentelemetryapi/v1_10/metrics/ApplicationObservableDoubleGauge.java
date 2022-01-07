/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableDoubleGauge;

public final class ApplicationObservableDoubleGauge implements ObservableDoubleGauge {

  public ApplicationObservableDoubleGauge(
      io.opentelemetry.api.metrics.ObservableDoubleGauge agentGauge) {}
}
