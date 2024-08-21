/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import io.opentelemetry.api.trace.Span

import javax.batch.api.chunk.listener.ItemReadListener

class CustomEventItemReadListener implements ItemReadListener {
  @Override
  void beforeRead() throws Exception {
    Span.current().addEvent("item.read.before")
  }

  @Override
  void afterRead(Object o) throws Exception {
    Span.current().addEvent("item.read.after")
  }

  @Override
  void onReadError(Exception e) throws Exception {
    Span.current().addEvent("item.read.error")
  }
}
