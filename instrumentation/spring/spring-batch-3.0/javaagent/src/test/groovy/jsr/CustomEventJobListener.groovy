/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import io.opentelemetry.api.trace.Span

import javax.batch.api.listener.JobListener

class CustomEventJobListener implements JobListener {
  @Override
  void beforeJob() throws Exception {
    Span.current().addEvent("job.before")
  }

  @Override
  void afterJob() throws Exception {
    Span.current().addEvent("job.after")
  }
}
