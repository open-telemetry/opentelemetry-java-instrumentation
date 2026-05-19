/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import io.opentelemetry.instrumentation.jmx.JmxMetricHandler;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MetricHandlerHolder {

  private final String handlerClassName;
  @Nullable private JmxMetricHandler handler;
  @Nullable private volatile DetectionStatus status;

  public MetricHandlerHolder(String handlerClassName) {
    this.handlerClassName = handlerClassName;
  }

  String getHandlerClassName() {
    return handlerClassName;
  }

  JmxMetricHandler getHandler() {
    return handler;
  }

  void setHandler(JmxMetricHandler handler) {
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
