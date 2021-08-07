/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import io.opentelemetry.api.trace.Span
import org.springframework.batch.core.ItemReadListener

class CustomEventItemReadListener implements ItemReadListener<String> {
  @Override
  void beforeRead() {
    Span.current().addEvent("item.read.before")
  }

  @Override
  void afterRead(String item) {
    Span.current().addEvent("item.read.after")
  }

  @Override
  void onReadError(Exception ex) {
    Span.current().addEvent("item.read.error")
  }
}
