/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import io.opentelemetry.api.trace.Span

import javax.batch.api.chunk.listener.ItemProcessListener

class CustomEventItemProcessListener implements ItemProcessListener {
  @Override
  void beforeProcess(Object o) throws Exception {
    Span.current().addEvent("item.process.before")
  }

  @Override
  void afterProcess(Object o, Object o1) throws Exception {
    Span.current().addEvent("item.process.after")
  }

  @Override
  void onProcessError(Object o, Exception e) throws Exception {
    Span.current().addEvent("item.process.error")
  }
}
