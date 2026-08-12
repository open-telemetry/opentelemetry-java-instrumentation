/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import io.opentelemetry.instrumentation.jmx.internal.ExperimentalJmxMetricHandler;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MetricHandlerHolder {

  private final String handlerName;
  @Nullable private ExperimentalJmxMetricHandler handler;
  @Nullable private volatile DetectionStatus status;

  public MetricHandlerHolder(String handlerName) {
    this.handlerName = handlerName;
  }

  String getHandlerName() {
    return handlerName;
  }

  @Nullable
  ExperimentalJmxMetricHandler getHandler() {
    return handler;
  }

  void setHandler(ExperimentalJmxMetricHandler handler) {
    if (this.handler != null) {
      throw new IllegalStateException("Handler is already set");
    }
    this.handler = handler;
  }

  synchronized boolean setStatus(DetectionStatus status) {
    boolean first = this.status == null;
    this.status = status;
    return first;
  }

  @Nullable
  DetectionStatus getStatus() {
    return status;
  }
}
