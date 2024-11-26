/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import io.opentelemetry.api.trace.Span;
import javax.batch.api.chunk.listener.ItemProcessListener;

class CustomEventItemProcessListener implements ItemProcessListener {
  @Override
  public void beforeProcess(Object o) {
    Span.current().addEvent("item.process.before");
  }

  @Override
  public void afterProcess(Object o, Object o1) {
    Span.current().addEvent("item.process.after");
  }

  @Override
  public void onProcessError(Object o, Exception e) {
    Span.current().addEvent("item.process.error");
  }
}
