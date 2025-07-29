/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import io.opentelemetry.api.trace.Span;
import javax.batch.api.listener.JobListener;

class CustomEventJobListener implements JobListener {
  @Override
  public void beforeJob() {
    Span.current().addEvent("job.before");
  }

  @Override
  public void afterJob() {
    Span.current().addEvent("job.after");
  }
}
