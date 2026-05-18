/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import io.opentelemetry.instrumentation.jmx.JmxMetricHandler;
import javax.annotation.Nullable;

class MetricHandlerExtractor {

  private final JmxMetricHandler handler;

  @Nullable private volatile DetectionStatus status;

  public MetricHandlerExtractor(JmxMetricHandler handler) {
    this.handler = handler;
  }

  JmxMetricHandler getHandler() {
    return handler;
  }

  void setStatus(DetectionStatus status) {
    this.status = status;
  }

  @Nullable
  DetectionStatus getStatus() {
    return status;
  }
}
