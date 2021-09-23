/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import io.opentelemetry.api.trace.Span

import javax.batch.api.chunk.listener.ItemWriteListener

class CustomEventItemWriteListener implements ItemWriteListener {
  @Override
  void beforeWrite(List<Object> list) throws Exception {
    Span.current().addEvent("item.write.before")
  }

  @Override
  void afterWrite(List<Object> list) throws Exception {
    Span.current().addEvent("item.write.after")
  }

  @Override
  void onWriteError(List<Object> list, Exception e) throws Exception {
    Span.current().addEvent("item.write.error")
  }
}
