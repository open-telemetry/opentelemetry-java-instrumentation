/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableDoubleCounter;

public final class ApplicationObservableDoubleCounter implements ObservableDoubleCounter {

  public ApplicationObservableDoubleCounter(
      io.opentelemetry.api.metrics.ObservableDoubleCounter agentCounter) {}
}
