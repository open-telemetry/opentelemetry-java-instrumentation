/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import javax.annotation.Nullable;

/**
 * A scheduler-level error reported by Quartz through {@code SchedulerListener.schedulerError}. This
 * is the "request" object that drives the scheduler-error {@link
 * io.opentelemetry.instrumentation.api.instrumenter.Instrumenter}.
 */
final class SchedulerError {

  private final String schedulerName;
  @Nullable private final String message;

  SchedulerError(String schedulerName, @Nullable String message) {
    this.schedulerName = schedulerName;
    this.message = message;
  }

  String getSchedulerName() {
    return schedulerName;
  }

  @Nullable
  String getMessage() {
    return message;
  }
}
