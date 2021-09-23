/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import io.opentelemetry.api.trace.Span
import org.springframework.batch.core.ItemProcessListener

class CustomEventItemProcessListener implements ItemProcessListener<String, Integer> {
  @Override
  void beforeProcess(String item) {
    Span.current().addEvent("item.process.before")
  }

  @Override
  void afterProcess(String item, Integer result) {
    Span.current().addEvent("item.process.after")
  }

  @Override
  void onProcessError(String item, Exception e) {
    Span.current().addEvent("item.process.error")
  }
}
