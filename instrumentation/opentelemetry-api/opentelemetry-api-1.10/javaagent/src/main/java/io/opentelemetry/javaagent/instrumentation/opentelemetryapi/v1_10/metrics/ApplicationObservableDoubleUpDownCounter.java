/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;

public final class ApplicationObservableDoubleUpDownCounter
    implements ObservableDoubleUpDownCounter {

  public ApplicationObservableDoubleUpDownCounter(
      io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter agentUpDownCounter) {}
}
