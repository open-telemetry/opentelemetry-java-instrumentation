/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import io.opentelemetry.instrumentation.jmx.JmxMetricHandler;
import javax.annotation.Nullable;

class MetricHandlerHolder {

  private final JmxMetricHandler handler;

  @Nullable private volatile DetectionStatus status;

  public MetricHandlerHolder(JmxMetricHandler handler) {
    this.handler = handler;
  }

  JmxMetricHandler getHandler() {
    return handler;
  }

  synchronized boolean setStatus(DetectionStatus status) {
    boolean first = this.status == null;
    this.status = status;
    return first;
  }

  @Nullable
  synchronized DetectionStatus getStatus() {
    return status;
  }
}
