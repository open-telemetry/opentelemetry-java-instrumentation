/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableLongCounter;

public final class ApplicationObservableLongCounter implements ObservableLongCounter {

  public ApplicationObservableLongCounter(
      io.opentelemetry.api.metrics.ObservableLongCounter agentCounter) {}
}
