/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import io.opentelemetry.api.trace.Span
import org.springframework.batch.core.ItemWriteListener

class CustomEventItemWriteListener implements ItemWriteListener<Integer> {
  @Override
  void beforeWrite(List<? extends Integer> items) {
    Span.current().addEvent("item.write.before")
  }

  @Override
  void afterWrite(List<? extends Integer> items) {
    Span.current().addEvent("item.write.after")
  }

  @Override
  void onWriteError(Exception exception, List<? extends Integer> items) {
    Span.current().addEvent("item.write.error")
  }
}
