/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import org.quartz.SchedulerException;

final class QuartzErrorCauseExtractor implements ErrorCauseExtractor {
  @Override
  public Throwable extract(Throwable error) {
    Throwable userError = error;
    while (userError instanceof SchedulerException) {
      userError = ((SchedulerException) userError).getUnderlyingException();
    }
    return userError;
  }
}
