/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

public final class ApplicationObservableLongUpDownCounter implements ObservableLongUpDownCounter {

  public ApplicationObservableLongUpDownCounter(
      io.opentelemetry.api.metrics.ObservableLongUpDownCounter agentUpDownCounter) {}
}
