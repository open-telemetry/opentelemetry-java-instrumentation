/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.internal.CauseUnwrapper;
import org.quartz.SchedulerException;

final class QuartzErrorCauseExtractor implements ErrorCauseExtractor {
  @Override
  public Throwable extract(Throwable error) {
    Throwable unwrapped =
        CauseUnwrapper.unwrap(
            error,
            candidate -> candidate instanceof SchedulerException,
            candidate -> ((SchedulerException) candidate).getUnderlyingException());
    return ErrorCauseExtractor.getDefault().extract(unwrapped);
  }
}
